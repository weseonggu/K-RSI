package com.service.RSIranking.integration.batch.live;

import com.service.RSIranking.config.krx_api.KrxApiProperties;
import com.service.RSIranking.integration.batch.support.LiveJobInvoker;
import com.service.RSIranking.repository.jpa.KosdaqStockRepository;
import com.service.RSIranking.repository.jpa.KospiStockRepository;
import com.service.RSIranking.schedule.AsyncJobLauncher;
import com.service.RSIranking.util.MarketDayForTheLast14Days;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import javax.sql.DataSource;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 실 dev 인프라(localhost:3308 / 3307 / 6380)에 직접 붙어 100일치 RSI 데이터를
 * 안정적으로 수집할 수 있는지 검증하는 재실행 가능한 러너.
 *
 * <h2>실행 방법</h2>
 * <pre>
 * ./gradlew test --tests Live100DayRsiCollectionRunner -Drsi.live.run=true
 *
 * # 옵션 (throttle 조정)
 * -Drsi.live.intervalMs=300
 * </pre>
 *
 * <h2>인프라 요구사항</h2>
 * <ul>
 *   <li>data DB MySQL: localhost:3308 (RSIData)</li>
 *   <li>meta DB MySQL: localhost:3307 (RSIMeta)</li>
 *   <li>Redis: localhost:6380</li>
 *   <li>KRX API key 유효성 ({@code application-dev.yml})</li>
 * </ul>
 *
 * <h2>실행 흐름</h2>
 * <ol>
 *   <li>preflight: 인프라 확인 + 현재 DB 상태 + resume 기준 로깅</li>
 *   <li>stockInfo 수집 (과거 → 최근, 영업일 114개)</li>
 *   <li>tradingInfo 수집 (과거 → 최근)</li>
 *   <li>RSI 계산 (영업일 인덱스 14 ~ 113 = 최근 100일)</li>
 *   <li>최종 상태 검증</li>
 * </ol>
 *
 * <h2>특징</h2>
 * <ul>
 *   <li>{@link AbstractIntegrationTest}를 상속하지 않음 — Testcontainers 사용 X.</li>
 *   <li>TRUNCATE 없음 — upsert 전제로 누적/갱신.</li>
 *   <li>resume 가능: 같은 일자가 이미 trading 행으로 존재하면 SKIP.</li>
 *   <li>부분 실패 허용: 한 일자 실패해도 즉시 중단하지 않음.</li>
 *   <li>{@code rsi.live.run=true} 가드로 CI 우발 실행 방지.</li>
 * </ul>
 *
 * <h2>예상 소요시간</h2>
 * 수집 약 660회 KRX API 호출 + throttle 200ms × 일자 → 수십 분.
 *
 * @see com.service.RSIranking.integration.batch.RealDataCollectionTest
 */
@Slf4j
@SpringBootTest
@ActiveProfiles("dev")
@TestPropertySource(properties = {
        // 러너 실행 중 자동 스케줄러가 중복으로 마스터 잡을 트리거하지 않도록 강제 비활성화
        "scheduler.master.enabled=false",
        "scheduler.stockinfo.enabled=false",
        "scheduler.dailytranding.enabled=false",
        "scheduler.rsiproducer.enabled=false",
        // RSI 계산 결과 메시지를 DB에 적재하는 listener consumer 는 활성화해야
        // produce된 메시지가 실제로 소비되어 daily_trading_information.rsi 컬럼에 반영됨
        "scheduler.rsistreamlistener.enabled=true"
})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@EnabledIfSystemProperty(named = "rsi.live.run", matches = "true")
class Live100DayRsiCollectionRunner {

    private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final String STREAM_KEY_PREFIX = "rsi:calculation:stream:";
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    /** lookback 14일 + 최근 100일 = 114 영업일. */
    private static final int TOTAL_MARKET_DAYS = 114;
    /** RSI 계산 대상 = 최근 100일. */
    private static final int RSI_COUNT = 100;
    /** RSI 계산 시작 인덱스 (앞 14개는 lookback 전용). */
    private static final int RSI_START_INDEX = TOTAL_MARKET_DAYS - RSI_COUNT;

    @Autowired private AsyncJobLauncher asyncJobLauncher;
    @Autowired private KrxApiProperties krxApiProperties;
    @Autowired private MarketDayForTheLast14Days marketDayForTheLast14Days;
    @Autowired private KospiStockRepository kospiStockRepository;
    @Autowired private KosdaqStockRepository kosdaqStockRepository;

    @Autowired
    @Qualifier("dataDBSource")
    private DataSource dataDataSource;

    @Autowired
    @Qualifier("rsiMessageRedisTemplate")
    private RedisTemplate<String, Object> rsiMessageRedisTemplate;

    private LiveJobInvoker invoker;
    private JdbcTemplate jdbcTemplate;
    private long throttleMs;

    private List<String> marketDays;
    private final List<DayResult> stockResults = new ArrayList<>();
    private final List<DayResult> tradingResults = new ArrayList<>();
    private final List<DayResult> rsiResults = new ArrayList<>();

    @BeforeAll
    void setUp() {
        this.invoker = new LiveJobInvoker(asyncJobLauncher, krxApiProperties, marketDayForTheLast14Days);
        this.jdbcTemplate = new JdbcTemplate(dataDataSource);
        this.throttleMs = Long.parseLong(System.getProperty("rsi.live.intervalMs", "200"));
        this.marketDays = generateMarketDays(TOTAL_MARKET_DAYS, resolveBaseDate());
        log.info("baseDate(end-of-range) = {}, marketDays[{}..{}] = {} ~ {}",
                marketDays.get(marketDays.size() - 1),
                0, marketDays.size() - 1,
                marketDays.get(0), marketDays.get(marketDays.size() - 1));
    }

    /**
     * RSI 수집 범위의 가장 최근 일자(base)를 결정한다.
     *
     * <p>우선순위:
     * <ol>
     *   <li>{@code -Drsi.live.endDate=yyyyMMdd} 명시 시 그 일자 (휴일/미응답 회피용)</li>
     *   <li>{@code -Drsi.live.lagDays=N} 만큼 어제로부터 더 과거 (KRX 데이터 lag 대응)</li>
     *   <li>기본값: KST 기준 어제(LocalDate.now(KST).minusDays(1))</li>
     * </ol>
     *
     * <p>KST 명시로 JVM default time zone 의존성을 제거한다.</p>
     */
    private static LocalDate resolveBaseDate() {
        String endDateProp = System.getProperty("rsi.live.endDate");
        if (endDateProp != null && !endDateProp.isBlank()) {
            return LocalDate.parse(endDateProp.trim(), YYYYMMDD);
        }
        long lagDays = Long.parseLong(System.getProperty("rsi.live.lagDays", "1"));
        return LocalDate.now(KST).minusDays(lagDays);
    }

    // ==================== Step 1: preflight ====================

    @Test
    @Order(1)
    @DisplayName("preflight: 인프라 확인 + resume 기준 파악")
    void preflight_checkInfra() {
        log.info("==========================================");
        log.info(" Live 100-day RSI Collection Runner");
        log.info(" intervalMs={}, marketDays={}, rsiTargets={}",
                throttleMs, marketDays.size(), RSI_COUNT);
        log.info(" 영업일 범위: {} ~ {}",
                marketDays.get(0), marketDays.get(marketDays.size() - 1));
        log.info("==========================================");

        // data DB
        Integer dataPing = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
        assertThat(dataPing).isEqualTo(1);

        // Redis PING
        String pong = rsiMessageRedisTemplate.getRequiredConnectionFactory()
                .getConnection().ping();
        log.info("Redis PING: {}", pong);
        assertThat(pong).isEqualToIgnoringCase("PONG");

        // 현재 DB 상태
        long kospiStocks = kospiStockRepository.count();
        long kosdaqStocks = kosdaqStockRepository.count();
        Integer kospiTradingRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM kospi_daily_trading_information", Integer.class);
        Integer kosdaqTradingRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM kosdaq_daily_trading_information", Integer.class);
        String kospiLastDate = jdbcTemplate.query(
                "SELECT MAX(date) FROM kospi_daily_trading_information",
                rs -> rs.next() ? rs.getString(1) : null);
        String kosdaqLastDate = jdbcTemplate.query(
                "SELECT MAX(date) FROM kosdaq_daily_trading_information",
                rs -> rs.next() ? rs.getString(1) : null);

        log.info("현재 KOSPI 종목: {} / KOSDAQ 종목: {}", kospiStocks, kosdaqStocks);
        log.info("현재 KOSPI 매매 행: {} (마지막 일자: {})", kospiTradingRows, kospiLastDate);
        log.info("현재 KOSDAQ 매매 행: {} (마지막 일자: {})", kosdaqTradingRows, kosdaqLastDate);

        // resume 기준: 이미 trading 행이 있는 일자 집합
        Set<String> alreadyCollected = collectedTradingDates();
        long alreadyInRange = marketDays.stream()
                .filter(alreadyCollected::contains)
                .count();
        log.info("이미 수집된 일자 (대상 범위 내): {} / {}", alreadyInRange, marketDays.size());
    }

    // ==================== Step 2: stockInfo ====================

    @Test
    @Order(2)
    @DisplayName("collect: 종목 정보 (과거 → 최근, 영업일 114개)")
    void collect_stockInfo_pastToRecent() {
        log.info("=== 종목 정보 수집 시작 ===");
        for (int i = 0; i < marketDays.size(); i++) {
            String date = marketDays.get(i);
            DayResult result = invokeWithMetrics(date, () -> invoker.launchStockInfoJob(date), "STOCK");
            stockResults.add(result);
            logProgress("STOCK", i, marketDays.size(), date, result, stockResults);
            sleepThrottle();
        }
        summarize("STOCK", stockResults);
    }

    // ==================== Step 3: tradingInfo ====================

    @Test
    @Order(3)
    @DisplayName("collect: 매매 정보 (과거 → 최근). 이미 수집된 일자는 SKIP")
    void collect_tradingInfo_pastToRecent() {
        log.info("=== 매매 정보 수집 시작 ===");
        Set<String> alreadyCollected = collectedTradingDates();

        for (int i = 0; i < marketDays.size(); i++) {
            String date = marketDays.get(i);
            DayResult result;
            if (alreadyCollected.contains(date)) {
                result = new DayResult(date, Status.SKIPPED_ALREADY_DONE, 0L, null);
            } else {
                result = invokeWithMetrics(date, () -> invoker.launchTradingInfoJob(date), "TRADING");
            }
            tradingResults.add(result);
            logProgress("TRADING", i, marketDays.size(), date, result, tradingResults);
            if (result.status == Status.SUCCESS) sleepThrottle();
        }
        summarize("TRADING", tradingResults);
    }

    // ==================== Step 4: RSI ====================

    @Test
    @Order(4)
    @DisplayName("calculate: RSI (영업일 인덱스 14 ~ 113, 총 100개. trading 없는 일자는 SKIP)")
    void calculate_rsi_pastToRecent() {
        log.info("=== RSI 계산 시작 (target days: index {} ~ {}) ===",
                RSI_START_INDEX, marketDays.size() - 1);

        // 휴일/미수집으로 trading row가 없는 일자에 RSI를 produce해도 listener 에서
        // "신규 종목 이므로 데이터가 더 필요합니다." 로그만 양산되어 의미 없음.
        // 실제 trading 행이 존재하는 일자 집합에 한해 produce 한다.
        Set<String> collected = collectedTradingDates();

        for (int i = RSI_START_INDEX; i < marketDays.size(); i++) {
            String date = marketDays.get(i);
            DayResult result;
            if (!collected.contains(date)) {
                result = new DayResult(date, Status.SKIPPED_ALREADY_DONE, 0L, "no trading row (holiday/uncollected)");
            } else {
                result = invokeWithMetrics(date, () -> invoker.launchRsiCalculationJob(date), "RSI");
            }
            rsiResults.add(result);
            int progressIdx = i - RSI_START_INDEX;
            logProgress("RSI", progressIdx, RSI_COUNT, date, result, rsiResults);
            if (result.status == Status.SUCCESS) sleepThrottle();
        }
        summarize("RSI", rsiResults);

        // RSI listener consumer (rsistreamlistener) 가 스트림 메시지를 비동기로 소비하므로,
        // produce가 끝났다고 곧바로 DB rsi 컬럼이 채워졌다고 가정할 수 없다.
        // verify_finalState가 정확한 결과를 보려면 consumer가 stream을 모두 소비할 시간을 준다.
        waitForRsiStreamDrain();
    }

    /**
     * KOSPI/KOSDAQ stream의 pending 메시지가 0이 되거나 timeout이 만료될 때까지 대기.
     * 정확한 metric은 XPENDING이지만, 간단히 stream length 안정화 + 짧은 추가 대기로 처리한다.
     * (consumer가 ack 후 XDEL을 호출하지 않으면 stream length는 줄지 않으므로, 여기서는
     * fixed delay로 충분한 처리 시간을 보장하는 식으로 구현)
     */
    private void waitForRsiStreamDrain() {
        long drainSecs = Long.parseLong(System.getProperty("rsi.live.drainSecs", "30"));
        log.info("RSI stream drain 대기 시작 ({}초) - listener consumer가 메시지를 처리할 시간", drainSecs);
        try {
            Thread.sleep(drainSecs * 1000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        log.info("RSI stream drain 대기 종료");
    }

    // ==================== Step 5: verify ====================

    @Test
    @Order(5)
    @DisplayName("verify: 최종 상태 검증")
    void verify_finalState() {
        long kospiStocks = kospiStockRepository.count();
        long kosdaqStocks = kosdaqStockRepository.count();
        Integer kospiTradingRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM kospi_daily_trading_information", Integer.class);
        Integer kosdaqTradingRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM kosdaq_daily_trading_information", Integer.class);
        Integer kospiUniqueDays = jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT date) FROM kospi_daily_trading_information", Integer.class);
        Integer kosdaqUniqueDays = jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT date) FROM kosdaq_daily_trading_information", Integer.class);

        Long kospiStreamLen = rsiMessageRedisTemplate.opsForStream()
                .size(STREAM_KEY_PREFIX + "KOSPI");
        Long kosdaqStreamLen = rsiMessageRedisTemplate.opsForStream()
                .size(STREAM_KEY_PREFIX + "KOSDAQ");

        String lastTargetDate = marketDays.get(marketDays.size() - 1);
        Integer kospiLastDayRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM kospi_daily_trading_information WHERE date = ?",
                Integer.class, lastTargetDate);
        Integer kosdaqLastDayRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM kosdaq_daily_trading_information WHERE date = ?",
                Integer.class, lastTargetDate);

        log.info("==========================================");
        log.info("       Live 100-day RSI 수집 결과");
        log.info("==========================================");
        log.info("KOSPI 종목 수            : {}", kospiStocks);
        log.info("KOSDAQ 종목 수           : {}", kosdaqStocks);
        log.info("KOSPI 매매 정보 행 수     : {}", kospiTradingRows);
        log.info("KOSDAQ 매매 정보 행 수    : {}", kosdaqTradingRows);
        log.info("KOSPI DISTINCT 일자       : {}", kospiUniqueDays);
        log.info("KOSDAQ DISTINCT 일자      : {}", kosdaqUniqueDays);
        log.info("최근 일자 ({}) KOSPI 행   : {}", lastTargetDate, kospiLastDayRows);
        log.info("최근 일자 ({}) KOSDAQ 행  : {}", lastTargetDate, kosdaqLastDayRows);
        log.info("KOSPI RSI Stream XLEN    : {}", kospiStreamLen);
        log.info("KOSDAQ RSI Stream XLEN   : {}", kosdaqStreamLen);
        log.info("==========================================");

        printFailedDays("STOCK", stockResults);
        printFailedDays("TRADING", tradingResults);
        printFailedDays("RSI", rsiResults);

        // 임계 검증 — 다른 환경 변수 없이 실행 가능한 최소 가정으로 판단
        assertThat(kospiStocks).as("KOSPI 종목 수").isGreaterThan(500);
        assertThat(kosdaqStocks).as("KOSDAQ 종목 수").isGreaterThan(1000);
        assertThat(kospiUniqueDays).as("KOSPI DISTINCT 매매 일자")
                .isGreaterThanOrEqualTo(50);
        assertThat(kosdaqUniqueDays).as("KOSDAQ DISTINCT 매매 일자")
                .isGreaterThanOrEqualTo(50);
        // 최근 일자가 휴장일이 아니라면 행이 있어야 함 — 있으면 검증, 없으면 경고만
        if (kospiLastDayRows == null || kospiLastDayRows == 0) {
            log.warn("최근 일자({}) KOSPI 행이 없음. 휴장일 가능성.", lastTargetDate);
        }
    }

    // ==================== 헬퍼 ====================

    /**
     * 기준일로부터 과거로 평일을 모아 N개의 영업일을 생성한다.
     * 결과는 과거 → 최근 순(가장 먼 과거가 [0]).
     */
    static List<String> generateMarketDays(int count, LocalDate base) {
        List<String> result = new ArrayList<>(count);
        LocalDate current = base;
        while (result.size() < count) {
            DayOfWeek dow = current.getDayOfWeek();
            if (dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY) {
                result.add(current.format(YYYYMMDD));
            }
            current = current.minusDays(1);
        }
        Collections.reverse(result);
        return result;
    }

    /**
     * 이미 수집된 trading 일자 집합 (resume / RSI SKIP 판정 기준).
     *
     * <p>{@code marketDays} 와 동일하게 {@code yyyyMMdd} 포맷으로 반환한다.
     * MySQL DATE 컬럼은 기본 변환 시 {@code yyyy-MM-dd} 로 나오므로
     * {@code DATE_FORMAT(...,'%Y%m%d')} 로 명시적으로 정렬한다.</p>
     */
    private Set<String> collectedTradingDates() {
        List<String> kospi = jdbcTemplate.queryForList(
                "SELECT DISTINCT DATE_FORMAT(date, '%Y%m%d') FROM kospi_daily_trading_information",
                String.class);
        List<String> kosdaq = jdbcTemplate.queryForList(
                "SELECT DISTINCT DATE_FORMAT(date, '%Y%m%d') FROM kosdaq_daily_trading_information",
                String.class);
        Set<String> set = new HashSet<>(kospi);
        set.addAll(kosdaq);
        return set;
    }

    private DayResult invokeWithMetrics(String date, ThrowingRunnable action, String tag) {
        long t0 = System.nanoTime();
        try {
            action.run();
            long ms = (System.nanoTime() - t0) / 1_000_000L;
            return new DayResult(date, Status.SUCCESS, ms, null);
        } catch (Exception e) {
            long ms = (System.nanoTime() - t0) / 1_000_000L;
            log.warn("[{}] 실패 - 일자: {}, 오류: {}", tag, date, e.getMessage());
            return new DayResult(date, Status.FAILED, ms, e.getMessage());
        }
    }

    private void logProgress(String tag, int idx, int total, String date,
                             DayResult result, List<DayResult> all) {
        if ((idx + 1) % 10 == 0 || idx == total - 1) {
            long success = all.stream().filter(r -> r.status == Status.SUCCESS).count();
            long failed = all.stream().filter(r -> r.status == Status.FAILED).count();
            long skipped = all.stream().filter(r -> r.status == Status.SKIPPED_ALREADY_DONE).count();
            log.info("[{}] {}/{} (성공:{}, 실패:{}, 스킵:{}) 마지막: {} ({} ms, {})",
                    tag, idx + 1, total, success, failed, skipped,
                    date, result.durationMs, result.status);
        }
    }

    private void summarize(String tag, List<DayResult> results) {
        long success = results.stream().filter(r -> r.status == Status.SUCCESS).count();
        long failed = results.stream().filter(r -> r.status == Status.FAILED).count();
        long skipped = results.stream().filter(r -> r.status == Status.SKIPPED_ALREADY_DONE).count();
        log.info("=== [{}] 종합 - 총 {}, 성공 {}, 실패 {}, 스킵 {} ===",
                tag, results.size(), success, failed, skipped);
    }

    private void printFailedDays(String tag, List<DayResult> results) {
        List<String> failedDates = results.stream()
                .filter(r -> r.status == Status.FAILED)
                .map(r -> r.date + "(" + r.errorMessage + ")")
                .toList();
        if (!failedDates.isEmpty()) {
            log.warn("[{}] 실패 일자 목록 ({}개): {}",
                    tag, failedDates.size(), failedDates);
        }
    }

    private void sleepThrottle() {
        if (throttleMs <= 0) return;
        try {
            Thread.sleep(throttleMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Status. */
    enum Status { SUCCESS, FAILED, SKIPPED_ALREADY_DONE }

    /** 한 일자에 대한 작업 결과. */
    record DayResult(String date, Status status, Long durationMs, String errorMessage) {}

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
