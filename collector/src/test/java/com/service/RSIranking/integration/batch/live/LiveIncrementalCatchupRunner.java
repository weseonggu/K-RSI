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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 운영 dev DB(localhost:3308 / 3307 / 6380)에서 마지막 매매 정보 일자를 읽고
 * 그 다음 영업일부터 어제까지의 갭을 채우는 증분 catch-up 러너.
 *
 * <h2>처리 흐름</h2>
 * <ol>
 *   <li>preflight: DB 마지막 trading 일자 / 처리 대상 갭 산출</li>
 *   <li>stockInfo 갱신: 갭에 해당하는 영업일에 대해 종목 정보 동기화</li>
 *   <li>tradingInfo 수집: 갭 영업일의 KOSPI/KOSDAQ 일별 매매 정보 수집</li>
 *   <li>RSI 계산: 갭 영업일에 대해 RSI 계산 (lookback 14일은 직전 backfill에서 적재되어 있어야 함)</li>
 *   <li>verify: 최종 상태 출력 및 간단 임계 검증</li>
 * </ol>
 *
 * <h2>실행 방법</h2>
 * <pre>
 * ./gradlew test --tests LiveIncrementalCatchupRunner -Drsi.live.run=true
 *
 * # 옵션
 * -Drsi.live.endDate=20260507   // 명시적 종료 일자(yyyyMMdd) — 미지정 시 KST 기준 어제
 * -Drsi.live.lagDays=2          // KRX 데이터 lag 대응 — 미지정 시 1 (어제)
 * -Drsi.live.intervalMs=300     // 일자 간 throttle — 미지정 시 200ms
 * -Drsi.live.drainSecs=15       // RSI listener drain 대기 — 미지정 시 15초
 * -Drsi.live.maxGap=20          // 안전장치: 처리 가능한 최대 영업일 수 — 미지정 시 30
 * </pre>
 *
 * <h2>전제 조건</h2>
 * <ul>
 *   <li>{@link Live100DayRsiCollectionRunner} 등으로 14일치 lookback이 이미 DB에 적재되어 있어야 한다.</li>
 *   <li>DB가 비어 있거나 lookback이 부족하면 RSI 계산은 "신규 종목 데이터 부족" 로그만 양산한다.</li>
 *   <li>{@code rsi.live.run=true} 가드로 CI 우발 실행을 방지한다.</li>
 * </ul>
 *
 * @see Live100DayRsiCollectionRunner
 */
@Slf4j
@SpringBootTest
@ActiveProfiles("dev")
@TestPropertySource(properties = {
        // 자동 스케줄러 비활성화 — 러너가 잡 호출의 단일 source-of-truth가 되도록
        "scheduler.master.enabled=false",
        "scheduler.stockinfo.enabled=false",
        "scheduler.dailytranding.enabled=false",
        "scheduler.rsiproducer.enabled=false",
        // RSI 결과를 DB에 적재하는 listener consumer 활성화
        "scheduler.rsistreamlistener.enabled=true"
})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@EnabledIfSystemProperty(named = "rsi.live.run", matches = "true")
class LiveIncrementalCatchupRunner {

    private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final String STREAM_KEY_PREFIX = "rsi:calculation:stream:";

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
    private long drainSecs;

    private LocalDate baseDate;        // 처리 종료 일자(포함)
    private LocalDate lastCollected;   // KOSPI/KOSDAQ 매매 정보의 안전한 최종 일자
    private List<String> targetDays;   // 처리 대상 영업일(yyyyMMdd, 과거→최근)

    private final List<DayResult> stockResults = new ArrayList<>();
    private final List<DayResult> tradingResults = new ArrayList<>();
    private final List<DayResult> rsiResults = new ArrayList<>();

    @BeforeAll
    void setUp() {
        this.invoker = new LiveJobInvoker(asyncJobLauncher, krxApiProperties, marketDayForTheLast14Days);
        this.jdbcTemplate = new JdbcTemplate(dataDataSource);
        this.throttleMs = Long.parseLong(System.getProperty("rsi.live.intervalMs", "200"));
        this.drainSecs = Long.parseLong(System.getProperty("rsi.live.drainSecs", "15"));
        this.baseDate = resolveBaseDate();
        this.lastCollected = findLastSafeCollectedDate();

        long maxGap = Long.parseLong(System.getProperty("rsi.live.maxGap", "30"));

        if (lastCollected == null) {
            log.warn("DB에 trading 행이 하나도 없습니다. Live100DayRsiCollectionRunner 로 backfill 후 재실행하세요.");
            this.targetDays = List.of();
        } else if (!lastCollected.isBefore(baseDate)) {
            log.info("이미 최신 상태입니다. lastCollected={}, baseDate={}", lastCollected, baseDate);
            this.targetDays = List.of();
        } else {
            LocalDate start = lastCollected.plusDays(1);
            this.targetDays = generateBusinessDays(start, baseDate);
            if (targetDays.size() > maxGap) {
                throw new IllegalStateException(String.format(
                        "처리 대상 영업일이 너무 많습니다 (%d개). 100일치 backfill 러너 사용을 검토하거나 -Drsi.live.maxGap 조정 필요. start=%s, baseDate=%s",
                        targetDays.size(), start, baseDate));
            }
        }

        log.info("==========================================");
        log.info(" Live Incremental Catch-up Runner");
        log.info(" baseDate(end)   = {}", baseDate);
        log.info(" lastCollected   = {}", lastCollected);
        log.info(" targetDays      = {} 개 영업일", targetDays.size());
        if (!targetDays.isEmpty()) {
            log.info(" 처리 범위       = {} ~ {}",
                    targetDays.get(0),
                    targetDays.get(targetDays.size() - 1));
        }
        log.info(" throttleMs={}, drainSecs={}", throttleMs, drainSecs);
        log.info("==========================================");
    }

    // ==================== Step 1: preflight ====================

    @Test
    @Order(1)
    @DisplayName("preflight: DB / Redis 인프라 점검 + 갭 일자 출력")
    void preflight_checkInfra() {
        // data DB
        Integer dataPing = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
        assertThat(dataPing).isEqualTo(1);

        // Redis PING
        String pong = rsiMessageRedisTemplate.getRequiredConnectionFactory()
                .getConnection().ping();
        log.info("Redis PING: {}", pong);
        assertThat(pong).isEqualToIgnoringCase("PONG");

        long kospiStocks = kospiStockRepository.count();
        long kosdaqStocks = kosdaqStockRepository.count();
        Integer kospiTradingRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM kospi_daily_trading_information", Integer.class);
        Integer kosdaqTradingRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM kosdaq_daily_trading_information", Integer.class);
        String kospiLast = jdbcTemplate.query(
                "SELECT DATE_FORMAT(MAX(date), '%Y-%m-%d') FROM kospi_daily_trading_information",
                rs -> rs.next() ? rs.getString(1) : null);
        String kosdaqLast = jdbcTemplate.query(
                "SELECT DATE_FORMAT(MAX(date), '%Y-%m-%d') FROM kosdaq_daily_trading_information",
                rs -> rs.next() ? rs.getString(1) : null);

        log.info("KOSPI stocks={}, KOSDAQ stocks={}", kospiStocks, kosdaqStocks);
        log.info("KOSPI trading rows={}, last date={}", kospiTradingRows, kospiLast);
        log.info("KOSDAQ trading rows={}, last date={}", kosdaqTradingRows, kosdaqLast);

        if (targetDays.isEmpty()) {
            log.info("처리 대상 일자가 없습니다. 이후 stage들은 즉시 종료됩니다.");
        } else {
            log.info("처리 대상 일자 목록: {}", targetDays);
        }
    }

    // ==================== Step 2: stockInfo ====================

    @Test
    @Order(2)
    @DisplayName("collect: 종목 정보 갱신 (갭 영업일)")
    void collect_stockInfo() {
        if (targetDays.isEmpty()) {
            log.info("[STOCK] 처리 대상 없음 — skip");
            return;
        }
        log.info("=== 종목 정보 갱신 시작 ({} 일) ===", targetDays.size());
        for (int i = 0; i < targetDays.size(); i++) {
            String date = targetDays.get(i);
            DayResult result = invokeWithMetrics(date, () -> invoker.launchStockInfoJob(date), "STOCK");
            stockResults.add(result);
            logProgress("STOCK", i, targetDays.size(), date, result, stockResults);
            if (result.status == Status.SUCCESS) sleepThrottle();
        }
        summarize("STOCK", stockResults);
    }

    // ==================== Step 3: tradingInfo ====================

    @Test
    @Order(3)
    @DisplayName("collect: 매매 정보 수집 (갭 영업일)")
    void collect_tradingInfo() {
        if (targetDays.isEmpty()) {
            log.info("[TRADING] 처리 대상 없음 — skip");
            return;
        }
        log.info("=== 매매 정보 수집 시작 ({} 일) ===", targetDays.size());
        for (int i = 0; i < targetDays.size(); i++) {
            String date = targetDays.get(i);
            DayResult result = invokeWithMetrics(date, () -> invoker.launchTradingInfoJob(date), "TRADING");
            tradingResults.add(result);
            logProgress("TRADING", i, targetDays.size(), date, result, tradingResults);
            if (result.status == Status.SUCCESS) sleepThrottle();
        }
        summarize("TRADING", tradingResults);
    }

    // ==================== Step 4: RSI ====================

    @Test
    @Order(4)
    @DisplayName("calculate: RSI (트레이딩 행이 적재된 갭 일자만)")
    void calculate_rsi() {
        if (targetDays.isEmpty()) {
            log.info("[RSI] 처리 대상 없음 — skip");
            return;
        }
        // KRX 휴장으로 trading row 적재 안 된 날에 RSI를 produce해도 listener 에서
        // "신규 종목 이므로 데이터가 더 필요합니다." 로그만 양산되어 무의미하다.
        Set<String> collectedAfterStep3 = collectedTradingDates();

        log.info("=== RSI 계산 시작 ({} 일, trading 없는 일자는 SKIP) ===", targetDays.size());
        for (int i = 0; i < targetDays.size(); i++) {
            String date = targetDays.get(i);
            DayResult result;
            if (!collectedAfterStep3.contains(date)) {
                result = new DayResult(date, Status.SKIPPED, 0L, "no trading row (holiday/uncollected)");
            } else {
                result = invokeWithMetrics(date, () -> invoker.launchRsiCalculationJob(date), "RSI");
            }
            rsiResults.add(result);
            logProgress("RSI", i, targetDays.size(), date, result, rsiResults);
            if (result.status == Status.SUCCESS) sleepThrottle();
        }
        summarize("RSI", rsiResults);

        // listener consumer 가 stream을 모두 비울 시간을 준다.
        waitForRsiStreamDrain();
    }

    private void waitForRsiStreamDrain() {
        log.info("RSI stream drain 대기 시작 ({}초)", drainSecs);
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
    @DisplayName("verify: 최종 상태 점검 + 임계 검증")
    void verify_finalState() {
        Long kospiStreamLen = rsiMessageRedisTemplate.opsForStream()
                .size(STREAM_KEY_PREFIX + "KOSPI");
        Long kosdaqStreamLen = rsiMessageRedisTemplate.opsForStream()
                .size(STREAM_KEY_PREFIX + "KOSDAQ");

        String newLastDate = jdbcTemplate.query(
                "SELECT DATE_FORMAT(MAX(date), '%Y-%m-%d') FROM kospi_daily_trading_information",
                rs -> rs.next() ? rs.getString(1) : null);
        String newLastRsiDate = jdbcTemplate.query(
                "SELECT DATE_FORMAT(MAX(date), '%Y-%m-%d') FROM kospi_daily_trading_information WHERE rsi IS NOT NULL",
                rs -> rs.next() ? rs.getString(1) : null);

        log.info("==========================================");
        log.info(" Incremental Catch-up 결과");
        log.info("==========================================");
        log.info(" baseDate                 : {}", baseDate);
        log.info(" KOSPI 마지막 매매 일자     : {}", newLastDate);
        log.info(" KOSPI 마지막 RSI 채워짐    : {}", newLastRsiDate);
        log.info(" KOSPI RSI Stream XLEN    : {}", kospiStreamLen);
        log.info(" KOSDAQ RSI Stream XLEN   : {}", kosdaqStreamLen);
        log.info("==========================================");

        printFailedDays("STOCK", stockResults);
        printFailedDays("TRADING", tradingResults);
        printFailedDays("RSI", rsiResults);

        if (targetDays.isEmpty()) {
            return;
        }
        // 임계 검증 — trading 단계에서 모든 일자가 실패하면 인프라/KRX 문제 가능성
        long tradingFailedAll = tradingResults.stream()
                .filter(r -> r.status == Status.FAILED).count();
        assertThat(tradingFailedAll)
                .as("TRADING 모든 일자 실패 — 인프라/KRX 점검 필요")
                .isLessThan(targetDays.size());
    }

    // ==================== 헬퍼 ====================

    /**
     * RSI 수집 갭의 종료 일자를 결정한다.
     * 우선순위:
     * <ol>
     *   <li>{@code -Drsi.live.endDate=yyyyMMdd}</li>
     *   <li>{@code -Drsi.live.lagDays=N} 만큼 오늘로부터 과거 (기본 1)</li>
     *   <li>KST 기준 어제</li>
     * </ol>
     */
    private static LocalDate resolveBaseDate() {
        String endDateProp = System.getProperty("rsi.live.endDate");
        if (endDateProp != null && !endDateProp.isBlank()) {
            return LocalDate.parse(endDateProp.trim(), YYYYMMDD);
        }
        long lagDays = Long.parseLong(System.getProperty("rsi.live.lagDays", "1"));
        return LocalDate.now(KST).minusDays(lagDays);
    }

    /**
     * KOSPI / KOSDAQ 매매 정보의 가장 최근 공통 안전 일자를 반환한다.
     * 두 시장 중 더 오래 전인 max(date)를 채택해 양 시장이 모두 채워진 마지막 일자를 보장한다.
     * 한쪽만 비어 있으면 null 반환.
     */
    private LocalDate findLastSafeCollectedDate() {
        String kospiMax = jdbcTemplate.query(
                "SELECT DATE_FORMAT(MAX(date), '%Y%m%d') FROM kospi_daily_trading_information",
                rs -> rs.next() ? rs.getString(1) : null);
        String kosdaqMax = jdbcTemplate.query(
                "SELECT DATE_FORMAT(MAX(date), '%Y%m%d') FROM kosdaq_daily_trading_information",
                rs -> rs.next() ? rs.getString(1) : null);
        if (kospiMax == null || kosdaqMax == null) {
            return null;
        }
        LocalDate kospi = LocalDate.parse(kospiMax, YYYYMMDD);
        LocalDate kosdaq = LocalDate.parse(kosdaqMax, YYYYMMDD);
        return kospi.isBefore(kosdaq) ? kospi : kosdaq;
    }

    /**
     * [start, end] 구간의 평일을 yyyyMMdd 포맷으로 반환한다.
     * 한국 공휴일은 별도로 거르지 않는다 — 휴장일은 trading 적재가 비어 있을 뿐
     * RSI 단계에서 자동 SKIP된다.
     */
    static List<String> generateBusinessDays(LocalDate startInclusive, LocalDate endInclusive) {
        List<String> result = new ArrayList<>();
        LocalDate cur = startInclusive;
        while (!cur.isAfter(endInclusive)) {
            DayOfWeek dow = cur.getDayOfWeek();
            if (dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY) {
                result.add(cur.format(YYYYMMDD));
            }
            cur = cur.plusDays(1);
        }
        return result;
    }

    /**
     * 적재된 trading 일자 집합을 yyyyMMdd 포맷으로 반환한다.
     * MySQL DATE 컬럼은 기본 변환 시 yyyy-MM-dd 이므로 DATE_FORMAT 으로 정렬한다.
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
        long success = all.stream().filter(r -> r.status == Status.SUCCESS).count();
        long failed = all.stream().filter(r -> r.status == Status.FAILED).count();
        long skipped = all.stream().filter(r -> r.status == Status.SKIPPED).count();
        log.info("[{}] {}/{} (성공:{}, 실패:{}, 스킵:{}) 일자: {} ({} ms, {})",
                tag, idx + 1, total, success, failed, skipped,
                date, result.durationMs, result.status);
    }

    private void summarize(String tag, List<DayResult> results) {
        long success = results.stream().filter(r -> r.status == Status.SUCCESS).count();
        long failed = results.stream().filter(r -> r.status == Status.FAILED).count();
        long skipped = results.stream().filter(r -> r.status == Status.SKIPPED).count();
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

    enum Status { SUCCESS, FAILED, SKIPPED }

    record DayResult(String date, Status status, Long durationMs, String errorMessage) {}

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
