package com.service.RSIranking.bootstrap;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * 기동 시 매매 정보 갭을 자동으로 채우는 캐치업(catch-up) 부트스트랩.
 *
 * <p>애플리케이션이 뜬 뒤 DB의 마지막 수집 일자를 확인하여,
 * 그 다음 영업일부터 어제(KST, lag 보정)까지의 갭을 수집합니다.
 * DB가 비어 있으면(첫 배포) 종료일로부터 {@code backfillDays}일 전부터 수집해
 * RSI 계산에 필요한 과거 데이터를 확보합니다. 이 하나의 로직이
 * 첫 배포(백필) / 서버 다운타임 복구(갭 캐치업) / 정상 기동(no-op)을 모두 처리합니다.</p>
 *
 * <h2>처리 흐름 (모두 날짜 오름차순 — RSI 스무딩이 전일 값을 이어받으므로 순서 필수)</h2>
 * <ol>
 *   <li>종목 정보 갱신 (꼬리 갭 영업일별)</li>
 *   <li>매매 정보 수집 (꼬리 갭 영업일별)</li>
 *   <li>내부 구멍 백필 — 마지막 수집일 이전인데 매매 행이 없는 주중 날짜를 추가 수집
 *       (수집기가 중간 기간 꺼져 있던 경우 복구, 휴장일은 빈 응답으로 자동 무시)</li>
 *   <li>RSI 계산 — 매매 갭과 별도로 "매매 정보는 있으나 RSI 미계산"인 일자를 직접 찾아 채운다
 *       (이전 실행이 중간에 죽어 RSI만 누락된 경우도 복구, 휴장일 자동 스킵)</li>
 * </ol>
 *
 * <h2>운영 특성</h2>
 * <ul>
 *   <li>{@code bootstrap.catchup.enabled=true}일 때만 활성화 (환경변수 RSI_BOOTSTRAP_ENABLED)</li>
 *   <li>별도 스레드에서 수행 — 애플리케이션 기동을 막지 않음</li>
 *   <li>멱등 삽입(ODKU) + (isu_cd, date) 유니크 제약으로 중복 실행에 안전</li>
 *   <li>일자별 실패는 기록 후 계속 진행 — 전체 실패 시에만 에러 로그</li>
 *   <li>RSI 결과 적재를 위해 {@code scheduler.rsistreamlistener.enabled=true} 필요</li>
 * </ul>
 *
 * @author RSIranking Team
 * @version 1.0
 * @see CatchupProperties
 * @see CollectionJobInvoker
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "bootstrap.catchup.enabled", havingValue = "true")
public class CatchupBootstrap implements ApplicationRunner {

    private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private static final String STREAM_KEY_PREFIX = "rsi:calculation:stream:";
    /** drain 폴링 간격(ms)과, 진전 없이 허용할 최대 폴링 횟수(리스너 비활성 등 안전장치) */
    private static final long DRAIN_POLL_MS = 10_000L;
    private static final int DRAIN_MAX_STALE_POLLS = 30;

    private final CatchupProperties properties;
    private final CollectionJobInvoker invoker;
    private final JdbcTemplate jdbcTemplate;
    private final RedisTemplate<String, Object> rsiMessageRedisTemplate;

    public CatchupBootstrap(CatchupProperties properties,
                            CollectionJobInvoker invoker,
                            @Qualifier("jdbcDataTemplate") JdbcTemplate jdbcTemplate,
                            @Qualifier("rsiMessageRedisTemplate") RedisTemplate<String, Object> rsiMessageRedisTemplate) {
        this.properties = properties;
        this.invoker = invoker;
        this.jdbcTemplate = jdbcTemplate;
        this.rsiMessageRedisTemplate = rsiMessageRedisTemplate;
    }

    /**
     * 기동 완료 후 캐치업을 별도 스레드로 시작합니다.
     *
     * @param args 애플리케이션 인자
     */
    @Override
    public void run(ApplicationArguments args) {
        Thread thread = new Thread(this::executeSafely, "catchup-bootstrap");
        thread.setDaemon(true);
        thread.start();
    }

    private void executeSafely() {
        try {
            execute();
        } catch (Exception e) {
            log.error("[CATCHUP] 캐치업 수행 중 예상치 못한 오류 — 애플리케이션은 계속 동작합니다", e);
        }
    }

    private void execute() {
        LocalDate endDate = LocalDate.now(KST).minusDays(properties.getLagDays());
        LocalDate lastCollected = findLastSafeCollectedDate();
        LocalDate startDate = (lastCollected == null)
                ? endDate.minusDays(properties.getBackfillDays())
                : lastCollected.plusDays(1);

        List<String> targetDays = startDate.isAfter(endDate)
                ? List.of()
                : generateBusinessDays(startDate, endDate);

        int stockFailed = 0;
        int tradingFailed = 0;
        if (targetDays.isEmpty()) {
            log.info("[CATCHUP] 매매 정보는 이미 최신 상태입니다. lastCollected={}, endDate={}", lastCollected, endDate);
        } else {
            log.info("==========================================");
            log.info(" Catch-up Bootstrap 시작");
            log.info(" lastCollected = {} {}", lastCollected, lastCollected == null ? "(빈 DB — 백필 모드)" : "");
            log.info(" 처리 범위     = {} ~ {} ({}개 영업일)",
                    targetDays.get(0), targetDays.get(targetDays.size() - 1), targetDays.size());
            log.info(" intervalMs={}, drainSecs={}", properties.getIntervalMs(), properties.getDrainSecs());
            log.info("==========================================");

            stockFailed = runPhase("STOCK", targetDays, invoker::launchStockInfoJob);
            tradingFailed = runPhase("TRADING", targetDays, invoker::launchTradingInfoJob);

            if (tradingFailed == targetDays.size()) {
                log.error("[CATCHUP] TRADING 전 일자 실패 — KRX API/인프라 점검이 필요합니다. RSI 계산을 생략합니다.");
                return;
            }
        }

        // 내부 구멍 백필: 마지막 수집일 이전인데 매매 행이 전혀 없는 주중 날짜.
        // (수집기가 중간 기간 동안 꺼져 있었던 경우 — max(date) 기준 꼬리 갭으로는 잡히지 않는다)
        // 휴장일도 후보에 섞이지만 KRX가 빈 응답을 반환해 행이 생기지 않을 뿐 무해하다.
        // 종목 마스터는 현재 상태 테이블이므로 과거 구멍에 대한 STOCK 재수집은 하지 않는다.
        if (lastCollected != null) {
            List<String> holeDays = findTradingHoleDates(
                    endDate.minusDays(properties.getBackfillDays()), lastCollected.minusDays(1));
            if (!holeDays.isEmpty()) {
                log.info("[CATCHUP] 내부 매매 구멍 {}개 일자 감지 ({} ~ {}) — 휴장일 포함 가능",
                        holeDays.size(), holeDays.get(0), holeDays.get(holeDays.size() - 1));
                tradingFailed += runPhase("TRADING-HOLE", holeDays, invoker::launchTradingInfoJob);
            }
        }

        // RSI는 매매 정보 갭과 별도로 "계산이 누락된 일자"를 직접 찾는다.
        // (이전 실행이 TRADING까지만 성공하고 죽으면 매매 갭은 없어도 RSI 갭은 남는다)
        // 휴장일은 trading 행 자체가 없어 자연히 대상에서 빠진다.
        List<String> rsiDays = findRsiPendingDates(endDate.minusDays(properties.getBackfillDays()), endDate);
        if (targetDays.isEmpty() && rsiDays.isEmpty()) {
            log.info("[CATCHUP] RSI도 최신 상태 — 할 일이 없습니다.");
            return;
        }
        log.info("[CATCHUP] RSI 대상 {}개 일자 (매매 정보는 있으나 RSI 미계산)", rsiDays.size());

        runPhase("RSI", rsiDays, invoker::launchRsiCalculationJob);
        waitForRsiStreamDrain();
        logFinalState(stockFailed, tradingFailed);
    }

    /**
     * 한 단계(종목/매매/RSI)를 날짜 오름차순으로 수행합니다. 일자별 실패는 기록 후 계속 진행합니다.
     *
     * @return 실패한 일자 수
     */
    private int runPhase(String tag, List<String> days, ThrowingDayAction action) {
        int failed = 0;
        for (int i = 0; i < days.size(); i++) {
            String day = days.get(i);
            try {
                action.run(day);
                log.info("[CATCHUP][{}] {}/{} 완료 - {}", tag, i + 1, days.size(), day);
                sleepThrottle();
            } catch (Exception e) {
                failed++;
                log.warn("[CATCHUP][{}] {}/{} 실패 - {}: {}", tag, i + 1, days.size(), day, e.getMessage());
            }
        }
        log.info("[CATCHUP][{}] 종합 - 총 {}, 성공 {}, 실패 {}", tag, days.size(), days.size() - failed, failed);
        return failed;
    }

    /**
     * KOSPI/KOSDAQ 매매 정보의 가장 최근 공통 안전 일자를 반환합니다.
     * 두 시장 중 더 과거인 max(date)를 채택해 양 시장이 모두 채워진 마지막 일자를 보장하며,
     * 한쪽이라도 비어 있으면 null(백필 모드)을 반환합니다.
     */
    private LocalDate findLastSafeCollectedDate() {
        String kospiMax = jdbcTemplate.query(
                "SELECT DATE_FORMAT(MAX(date), '%Y%m%d') FROM kospi_daily_trading_information",
                rs -> rs.next() ? rs.getString(1) : null);
        String kosdaqMax = jdbcTemplate.query(
                "SELECT DATE_FORMAT(MAX(date), '%Y%m%d') FROM kosdaq_daily_trading_information",
                rs -> rs.next() ? rs.getString(1) : null);
        String etfMax = jdbcTemplate.query(
                "SELECT DATE_FORMAT(MAX(date), '%Y%m%d') FROM etf_daily_trading_information",
                rs -> rs.next() ? rs.getString(1) : null);
        if (kospiMax == null || kosdaqMax == null || etfMax == null) {
            return null;
        }
        LocalDate kospi = LocalDate.parse(kospiMax, YYYYMMDD);
        LocalDate kosdaq = LocalDate.parse(kosdaqMax, YYYYMMDD);
        LocalDate etf = LocalDate.parse(etfMax, YYYYMMDD);
        return List.of(kospi, kosdaq, etf).stream().min(LocalDate::compareTo).orElse(null);
    }

    /**
     * [start, end] 구간의 평일을 yyyyMMdd 포맷으로 반환합니다.
     * 한국 공휴일은 따로 거르지 않습니다 — 휴장일은 trading 적재가 비어 RSI 단계에서 자동 스킵됩니다.
     */
    private static List<String> generateBusinessDays(LocalDate startInclusive, LocalDate endInclusive) {
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
     * [start, end] 구간에서 매매 행이 전혀 없는 주중 날짜(내부 구멍 후보)를
     * yyyyMMdd 포맷 오름차순으로 반환합니다. 휴장일도 섞이지만 수집 시 빈 응답으로 무해합니다.
     */
    private List<String> findTradingHoleDates(LocalDate start, LocalDate end) {
        if (start.isAfter(end)) {
            return List.of();
        }
        String kospiSql = "SELECT DISTINCT DATE_FORMAT(date, '%Y%m%d')"
                + " FROM kospi_daily_trading_information WHERE date BETWEEN ? AND ?";
        String kosdaqSql = "SELECT DISTINCT DATE_FORMAT(date, '%Y%m%d')"
                + " FROM kosdaq_daily_trading_information WHERE date BETWEEN ? AND ?";
        String etfSql = "SELECT DISTINCT DATE_FORMAT(date, '%Y%m%d')"
                + " FROM etf_daily_trading_information WHERE date BETWEEN ? AND ?";
        Set<String> collected = new TreeSet<>(jdbcTemplate.queryForList(kospiSql, String.class, start, end));
        collected.retainAll(jdbcTemplate.queryForList(kosdaqSql, String.class, start, end));
        collected.retainAll(jdbcTemplate.queryForList(etfSql, String.class, start, end));
        return generateBusinessDays(start, end).stream()
                .filter(day -> !collected.contains(day))
                .toList();
    }

    /**
     * [start, end] 구간에서 매매 정보는 적재됐지만 RSI가 한 건도 계산되지 않은 일자를
     * yyyyMMdd 포맷 오름차순으로 반환합니다.
     *
     * <p>{@code COUNT(rsi) = 0}: 해당 일자 전체가 RSI 미계산이라는 뜻이다.
     * (거래정지 등으로 일부 종목만 null인 정상 일자는 대상에서 제외된다)
     * 백필 초기 14 영업일은 lookback 부족으로 RSI가 채워지지 않아 재기동 때마다
     * 다시 시도되지만, 일자당 수 초 수준이라 허용한다.</p>
     */
    private List<String> findRsiPendingDates(LocalDate start, LocalDate end) {
        // String.format 사용 금지 — DATE_FORMAT 의 %Y 가 포맷 지시자로 해석되어 예외가 난다
        String kospiSql = "SELECT DATE_FORMAT(date, '%Y%m%d') FROM kospi_daily_trading_information"
                + " WHERE date BETWEEN ? AND ? GROUP BY date HAVING COUNT(rsi) = 0";
        String kosdaqSql = "SELECT DATE_FORMAT(date, '%Y%m%d') FROM kosdaq_daily_trading_information"
                + " WHERE date BETWEEN ? AND ? GROUP BY date HAVING COUNT(rsi) = 0";
        String etfSql = "SELECT DATE_FORMAT(date, '%Y%m%d') FROM etf_daily_trading_information"
                + " WHERE date BETWEEN ? AND ? GROUP BY date HAVING COUNT(rsi) = 0";
        List<String> kospi = jdbcTemplate.queryForList(kospiSql, String.class, start, end);
        List<String> kosdaq = jdbcTemplate.queryForList(kosdaqSql, String.class, start, end);
        List<String> etf = jdbcTemplate.queryForList(etfSql, String.class, start, end);
        Set<String> union = new TreeSet<>(kospi);
        union.addAll(kosdaq);
        union.addAll(etf);
        return new ArrayList<>(union);
    }

    /**
     * RSI 스트림이 빌 때까지 대기합니다.
     *
     * <p>백필 시 수만 건의 메시지가 쌓여 리스너 소비가 생산보다 한참 늦으므로
     * 고정 sleep 이 아니라 XLEN 폴링으로 대기한다. 잔량이 {@value #DRAIN_MAX_STALE_POLLS}회
     * 연속으로 줄지 않으면(리스너 비활성 등) 경고 후 중단한다. 스트림이 빈 뒤에는
     * in-flight 메시지 처리를 위해 {@code drainSecs}만큼 추가 대기한다.</p>
     */
    private void waitForRsiStreamDrain() {
        long lastTotal = Long.MAX_VALUE;
        int stalePolls = 0;
        while (true) {
            long total = streamLength("KOSPI") + streamLength("KOSDAQ") + streamLength("ETF");
            if (total == 0) {
                break;
            }
            stalePolls = (total >= lastTotal) ? stalePolls + 1 : 0;
            if (stalePolls >= DRAIN_MAX_STALE_POLLS) {
                log.warn("[CATCHUP] RSI 스트림 잔량 {}건이 줄지 않아 drain 대기를 중단합니다 "
                        + "— scheduler.rsistreamlistener.enabled 설정을 확인하세요", total);
                return;
            }
            lastTotal = total;
            log.info("[CATCHUP] RSI 스트림 drain 대기 — 남은 메시지 {}건", total);
            if (!sleepQuietly(DRAIN_POLL_MS)) {
                return;
            }
        }
        // 스트림은 비었지만 리스너가 처리 중인 마지막 메시지들을 위해 잠시 더 기다린다
        sleepQuietly(properties.getDrainSecs() * 1000L);
    }

    private long streamLength(String market) {
        try {
            Long size = rsiMessageRedisTemplate.opsForStream().size(STREAM_KEY_PREFIX + market);
            return size == null ? 0L : size;
        } catch (Exception e) {
            log.warn("[CATCHUP] RSI 스트림 길이 조회 실패 ({}): {}", market, e.getMessage());
            return 0L;
        }
    }

    /** @return 인터럽트 없이 잠들었으면 true */
    private static boolean sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private void logFinalState(int stockFailed, int tradingFailed) {
        String lastDate = jdbcTemplate.query(
                "SELECT DATE_FORMAT(MAX(date), '%Y-%m-%d') FROM kospi_daily_trading_information",
                rs -> rs.next() ? rs.getString(1) : null);
        String lastRsiDate = jdbcTemplate.query(
                "SELECT DATE_FORMAT(MAX(date), '%Y-%m-%d') FROM kospi_daily_trading_information WHERE rsi IS NOT NULL",
                rs -> rs.next() ? rs.getString(1) : null);
        log.info("==========================================");
        log.info(" Catch-up Bootstrap 완료");
        log.info(" KOSPI 마지막 매매 일자  : {}", lastDate);
        log.info(" KOSPI 마지막 RSI 일자   : {}", lastRsiDate);
        log.info(" 실패 일자 수            : STOCK={}, TRADING={}", stockFailed, tradingFailed);
        log.info("==========================================");
    }

    private void sleepThrottle() {
        if (properties.getIntervalMs() <= 0) return;
        try {
            Thread.sleep(properties.getIntervalMs());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @FunctionalInterface
    private interface ThrowingDayAction {
        void run(String day) throws Exception;
    }
}
