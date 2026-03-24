package com.service.RSIranking.integration.batch;

import com.service.RSIranking.config.krx_api.KrxApiProperties;
import com.service.RSIranking.integration.AbstractIntegrationTest;
import com.service.RSIranking.repository.jpa.KosdaqStockRepository;
import com.service.RSIranking.repository.jpa.KospiStockRepository;
import com.service.RSIranking.schedule.AsyncJobLauncher;
import com.service.RSIranking.util.MarketDayForTheLast14Days;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.text.SimpleDateFormat;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 실제 KRX API를 호출하여 100일치 데이터를 수집하는 통합 테스트.
 *
 * <p>Testcontainers의 빈 DB에서 시작하여 실제 데이터를 수집하고
 * DB에 정상 적재되었는지 검증합니다.</p>
 *
 * <p>Launcher Bean들은 {@code @ConditionalOnProperty}로 인해 테스트에서 생성되지 않으므로,
 * {@link AsyncJobLauncher}와 {@link KrxApiProperties}를 직접 주입하여
 * Launcher의 로직을 테스트 내에서 수행합니다.</p>
 *
 * <h2>실행 순서</h2>
 * <ol>
 *   <li>DB 전체 초기화 (TRUNCATE)</li>
 *   <li>100일치 종목 정보 수집 (stockUpdateJob)</li>
 *   <li>100일치 매매 정보 수집 (dailyTradingInformationUpdateJob)</li>
 *   <li>RSI 계산 실행 (RSICalculationJob)</li>
 *   <li>최종 데이터 검증</li>
 * </ol>
 *
 * <h2>주의사항</h2>
 * <ul>
 *   <li>실제 KRX API를 호출하므로 네트워크 연결 필요</li>
 *   <li>API 호출 건수가 많아 실행 시간이 길 수 있음</li>
 *   <li>Docker Desktop 실행 필요 (Testcontainers)</li>
 * </ul>
 */
@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RealDataCollectionTest extends AbstractIntegrationTest {

    @Autowired
    private AsyncJobLauncher asyncJobLauncher;

    @Autowired
    private KrxApiProperties krxApiProperties;

    @Autowired
    private MarketDayForTheLast14Days marketDayForTheLast14Days;

    @Autowired
    private KospiStockRepository kospiStockRepository;

    @Autowired
    private KosdaqStockRepository kosdaqStockRepository;

    @Autowired
    @Qualifier("dataDBSource")
    private DataSource dataDataSource;

    private JdbcTemplate jdbcTemplate;

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    /**
     * 100일치 영업일(평일) 목록을 생성합니다.
     * 기준일: 2025-03-14 (금요일)부터 과거로 100 영업일.
     * 과거 날짜가 리스트 앞, 최신 날짜가 뒤 순서입니다. (과거 → 최신 순)
     */
    private static List<String> generate100MarketDays() {
        List<String> marketDays = new ArrayList<>();
        LocalDate current = LocalDate.of(2025, 3, 14);

        while (marketDays.size() < 100) {
            DayOfWeek dow = current.getDayOfWeek();
            if (dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY) {
                marketDays.add(current.format(DATE_FORMAT));
            }
            current = current.minusDays(1);
        }

        // 과거 → 최신 순으로 정렬 (가장 먼 과거부터 수집)
        java.util.Collections.reverse(marketDays);
        return marketDays;
    }

    @BeforeEach
    void setUp() {
        jdbcTemplate = new JdbcTemplate(dataDataSource);
    }

    // ==================== 종목 정보 수집 (StockInfoLauncher 로직) ====================

    private void launchStockInfoJob(String yesterday) throws Exception {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss");
        String date = dateFormat.format(new Date());

        JobParameters kospiParams = new JobParametersBuilder()
                .addString("date", date)
                .addString("apiUrl", krxApiProperties.getKospiInfoUrl())
                .addString("apiKey", krxApiProperties.getKey())
                .addString("mktNm", "KOSPI")
                .addString("yesterday", yesterday)
                .toJobParameters();

        JobParameters kosdaqParams = new JobParametersBuilder()
                .addString("date", date)
                .addString("apiUrl", krxApiProperties.getKosdaqInfoUrl())
                .addString("apiKey", krxApiProperties.getKey())
                .addString("mktNm", "KOSDAQ")
                .addString("yesterday", yesterday)
                .toJobParameters();

        CompletableFuture<Void> kospiFuture = asyncJobLauncher.runKospiInfoJob(kospiParams);
        CompletableFuture<Void> kosdaqFuture = asyncJobLauncher.runKosdaqInfoJob(kosdaqParams);

        CompletableFuture.allOf(kospiFuture, kosdaqFuture).get();
    }

    // ==================== 매매 정보 수집 (DailyTradingInfoLauncher 로직) ====================

    private void launchTradingInfoJob(String yesterday) throws Exception {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss");
        String date = dateFormat.format(new Date());

        JobParameters kospiParams = new JobParametersBuilder()
                .addString("uuid", UUID.randomUUID().toString())
                .addString("date", date)
                .addString("apiUrl", krxApiProperties.getKospiTradingInfoUrl())
                .addString("apiKey", krxApiProperties.getKey())
                .addString("mktNm", "KOSPI")
                .addString("yesterday", yesterday)
                .toJobParameters();

        JobParameters kosdaqParams = new JobParametersBuilder()
                .addString("uuid", UUID.randomUUID().toString())
                .addString("date", date)
                .addString("apiUrl", krxApiProperties.getKosdaqTradingInfoUrl())
                .addString("apiKey", krxApiProperties.getKey())
                .addString("mktNm", "KOSDAQ")
                .addString("yesterday", yesterday)
                .toJobParameters();

        CompletableFuture<Void> kospiFuture = asyncJobLauncher.runKospiTradingJob(kospiParams);
        CompletableFuture<Void> kosdaqFuture = asyncJobLauncher.runKosdaqTradingJob(kosdaqParams);

        CompletableFuture.allOf(kospiFuture, kosdaqFuture).get();
    }

    // ==================== RSI 계산 (RSICalculationLauncher 로직) ====================

    private void launchRSICalculationJob(String targetDate) throws Exception {
        List<LocalDate> marketDay = marketDayForTheLast14Days.getMarketDayForTheLast14Days(targetDate);

        String marketDayListString = marketDay.stream()
                .map(ld -> ld.format(DateTimeFormatter.ofPattern("yyyyMMdd")))
                .collect(Collectors.joining(","));

        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss");
        String jobExecutionTimestamp = dateFormat.format(new Date());

        JobParameters kospiParams = new JobParametersBuilder()
                .addString("date", jobExecutionTimestamp)
                .addString("targetDate", targetDate)
                .addString("apiUrl", krxApiProperties.getKospiInfoUrl())
                .addString("apiKey", krxApiProperties.getKey())
                .addString("mktNm", "KOSPI")
                .addString("marketDayList", marketDayListString)
                .toJobParameters();

        JobParameters kosdaqParams = new JobParametersBuilder()
                .addString("date", jobExecutionTimestamp)
                .addString("targetDate", targetDate)
                .addString("apiUrl", krxApiProperties.getKosdaqInfoUrl())
                .addString("apiKey", krxApiProperties.getKey())
                .addString("mktNm", "KOSDAQ")
                .addString("marketDayList", marketDayListString)
                .toJobParameters();

        CompletableFuture<Void> kospiFuture = asyncJobLauncher.runKospiRSICalculationJob(kospiParams);
        CompletableFuture<Void> kosdaqFuture = asyncJobLauncher.runKosdaqRSICalculationJob(kosdaqParams);

        CompletableFuture.allOf(kospiFuture, kosdaqFuture).get();
    }

    // ==================== Step 0: DB 초기화 ====================

    @Test
    @Order(1)
    @DisplayName("Step 0: DB 전체 초기화 - 모든 테이블 비우기")
    void step0_truncateAllTables() {
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 0");

        jdbcTemplate.execute("TRUNCATE TABLE kospi_daily_trading_information");
        jdbcTemplate.execute("TRUNCATE TABLE kosdaq_daily_trading_information");
        jdbcTemplate.execute("TRUNCATE TABLE kospi_stock_info");
        jdbcTemplate.execute("TRUNCATE TABLE kosdaq_stock_info");

        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 1");

        Integer kospiStockCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM kospi_stock_info", Integer.class);
        Integer kosdaqStockCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM kosdaq_stock_info", Integer.class);
        Integer kospiTradingCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM kospi_daily_trading_information", Integer.class);
        Integer kosdaqTradingCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM kosdaq_daily_trading_information", Integer.class);

        assertThat(kospiStockCount).isZero();
        assertThat(kosdaqStockCount).isZero();
        assertThat(kospiTradingCount).isZero();
        assertThat(kosdaqTradingCount).isZero();

        log.info("=== DB 초기화 완료 ===");
    }

    // ==================== Step 1: 종목 정보 수집 ====================

    @Test
    @Order(2)
    @DisplayName("Step 1: 100일치 종목 정보 수집 (KOSPI + KOSDAQ)")
    void step1_collectStockInfo() throws Exception {
        List<String> marketDays = generate100MarketDays();

        log.info("=== 종목 정보 수집 시작 ===");
        log.info("수집 대상: {}일", marketDays.size());
        log.info("시작일: {} ~ 종료일: {}", marketDays.get(marketDays.size() - 1), marketDays.get(0));

        int successCount = 0;
        int failCount = 0;

        for (int i = 0; i < marketDays.size(); i++) {
            String date = marketDays.get(i);
            try {
                launchStockInfoJob(date);
                successCount++;
                if ((i + 1) % 10 == 0) {
                    log.info("[종목 정보] 진행: {}/{} (성공: {}, 실패: {}) - 현재: {}",
                            i + 1, marketDays.size(), successCount, failCount, date);
                }
            } catch (Exception e) {
                failCount++;
                log.error("[종목 정보] 실패 - 날짜: {}, 오류: {}", date, e.getMessage());
            }
        }

        log.info("=== 종목 정보 수집 완료 - 성공: {}, 실패: {} ===", successCount, failCount);

        long kospiCount = kospiStockRepository.count();
        long kosdaqCount = kosdaqStockRepository.count();

        log.info("KOSPI 종목 수: {}, KOSDAQ 종목 수: {}", kospiCount, kosdaqCount);

        assertThat(kospiCount).isGreaterThan(0);
        assertThat(kosdaqCount).isGreaterThan(0);
    }

    // ==================== Step 2: 매매 정보 수집 ====================

    @Test
    @Order(3)
    @DisplayName("Step 2: 100일치 매매 정보 수집 (KOSPI + KOSDAQ)")
    void step2_collectTradingInfo() throws Exception {
        List<String> marketDays = generate100MarketDays();

        log.info("=== 매매 정보 수집 시작 ===");
        log.info("수집 대상: {}일", marketDays.size());

        int successCount = 0;
        int failCount = 0;

        for (int i = 0; i < marketDays.size(); i++) {
            String date = marketDays.get(i);
            try {
                launchTradingInfoJob(date);
                successCount++;
                if ((i + 1) % 10 == 0) {
                    log.info("[매매 정보] 진행: {}/{} (성공: {}, 실패: {}) - 현재: {}",
                            i + 1, marketDays.size(), successCount, failCount, date);
                }
            } catch (Exception e) {
                failCount++;
                log.error("[매매 정보] 실패 - 날짜: {}, 오류: {}", date, e.getMessage());
            }
        }

        log.info("=== 매매 정보 수집 완료 - 성공: {}, 실패: {} ===", successCount, failCount);

        Integer kospiTradingCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM kospi_daily_trading_information", Integer.class);
        Integer kosdaqTradingCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM kosdaq_daily_trading_information", Integer.class);

        log.info("KOSPI 매매 정보 수: {}, KOSDAQ 매매 정보 수: {}", kospiTradingCount, kosdaqTradingCount);

        assertThat(kospiTradingCount).isGreaterThan(0);
        assertThat(kosdaqTradingCount).isGreaterThan(0);
    }

    // ==================== Step 3: RSI 계산 ====================

    @Test
    @Order(4)
    @DisplayName("Step 3: RSI 계산 실행 (최신 날짜 기준)")
    void step3_calculateRSI() throws Exception {
        String targetDate = "20250314";

        log.info("=== RSI 계산 시작 - 대상일: {} ===", targetDate);

        launchRSICalculationJob(targetDate);

        log.info("=== RSI 계산 완료 ===");
    }

    // ==================== Step 4: 최종 데이터 검증 ====================

    @Test
    @Order(5)
    @DisplayName("Step 4: 최종 데이터 검증 - 모든 테이블에 데이터가 적재되었는지 확인")
    void step4_verifyAllData() {
        long kospiStockCount = kospiStockRepository.count();
        long kosdaqStockCount = kosdaqStockRepository.count();

        Integer kospiTradingCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM kospi_daily_trading_information", Integer.class);
        Integer kosdaqTradingCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM kosdaq_daily_trading_information", Integer.class);

        Integer kospiUniqueDays = jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT date) FROM kospi_daily_trading_information", Integer.class);
        Integer kosdaqUniqueDays = jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT date) FROM kosdaq_daily_trading_information", Integer.class);

        log.info("==========================================");
        log.info("       100일치 데이터 수집 최종 결과");
        log.info("==========================================");
        log.info("KOSPI 종목 수        : {}", kospiStockCount);
        log.info("KOSDAQ 종목 수       : {}", kosdaqStockCount);
        log.info("KOSPI 매매 정보 수   : {}", kospiTradingCount);
        log.info("KOSDAQ 매매 정보 수  : {}", kosdaqTradingCount);
        log.info("KOSPI 매매 일수      : {}", kospiUniqueDays);
        log.info("KOSDAQ 매매 일수     : {}", kosdaqUniqueDays);
        log.info("==========================================");

        assertThat(kospiStockCount).as("KOSPI 종목 수").isGreaterThan(500);
        assertThat(kosdaqStockCount).as("KOSDAQ 종목 수").isGreaterThan(1000);
        assertThat(kospiTradingCount).as("KOSPI 매매 정보 수").isGreaterThan(0);
        assertThat(kosdaqTradingCount).as("KOSDAQ 매매 정보 수").isGreaterThan(0);
        assertThat(kospiUniqueDays).as("KOSPI 매매 일수").isGreaterThanOrEqualTo(50);
        assertThat(kosdaqUniqueDays).as("KOSDAQ 매매 일수").isGreaterThanOrEqualTo(50);
    }
}
