package com.service.RSIranking.integration.batch.support;

import com.service.RSIranking.config.krx_api.KrxApiProperties;
import com.service.RSIranking.schedule.AsyncJobLauncher;
import com.service.RSIranking.util.MarketDayForTheLast14Days;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * 실 데이터 수집 테스트를 위한 Job 호출 헬퍼.
 *
 * <p>{@link AsyncJobLauncher}로 KOSPI/KOSDAQ Job을 병렬 실행하고
 * {@link CompletableFuture#allOf}로 완료 대기한다. 기존 Launcher 빈은
 * {@code @ConditionalOnProperty}로 비활성화되므로, 테스트가 직접 동일 로직을 수행한다.</p>
 *
 * <p>Spring Bean이 아닌 일반 헬퍼다. 테스트가 자기 의존성을 받아 인스턴스를 만든다.</p>
 *
 * @see com.service.RSIranking.integration.batch.RealDataCollectionTest
 * @see com.service.RSIranking.integration.batch.live.Live100DayRsiCollectionRunner
 */
public class LiveJobInvoker {

    private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final SimpleDateFormat TIMESTAMP_FMT = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss");

    private final AsyncJobLauncher asyncJobLauncher;
    private final KrxApiProperties krxApiProperties;
    private final MarketDayForTheLast14Days marketDayForTheLast14Days;

    public LiveJobInvoker(AsyncJobLauncher asyncJobLauncher,
                          KrxApiProperties krxApiProperties,
                          MarketDayForTheLast14Days marketDayForTheLast14Days) {
        this.asyncJobLauncher = asyncJobLauncher;
        this.krxApiProperties = krxApiProperties;
        this.marketDayForTheLast14Days = marketDayForTheLast14Days;
    }

    /**
     * KOSPI/KOSDAQ 종목 정보 Job을 yesterday 기준으로 병렬 실행하고 완료 대기한다.
     */
    public void launchStockInfoJob(String yesterday) throws Exception {
        String date = TIMESTAMP_FMT.format(new Date());

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

        CompletableFuture<Void> kospi = asyncJobLauncher.runKospiInfoJob(kospiParams);
        CompletableFuture<Void> kosdaq = asyncJobLauncher.runKosdaqInfoJob(kosdaqParams);

        CompletableFuture.allOf(kospi, kosdaq).get();
    }

    /**
     * KOSPI/KOSDAQ 일별 매매 정보 Job을 yesterday 기준으로 병렬 실행하고 완료 대기한다.
     */
    public void launchTradingInfoJob(String yesterday) throws Exception {
        String date = TIMESTAMP_FMT.format(new Date());

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

        CompletableFuture<Void> kospi = asyncJobLauncher.runKospiTradingJob(kospiParams);
        CompletableFuture<Void> kosdaq = asyncJobLauncher.runKosdaqTradingJob(kosdaqParams);

        CompletableFuture.allOf(kospi, kosdaq).get();
    }

    /**
     * KOSPI/KOSDAQ RSI 계산 Job을 targetDate 기준으로 병렬 실행하고 완료 대기한다.
     *
     * <p>targetDate 기준 과거 13 영업일 + targetDate = 14일치 lookback으로 RSI를 계산한다.</p>
     */
    public void launchRsiCalculationJob(String targetDate) throws Exception {
        List<LocalDate> marketDays = marketDayForTheLast14Days.getMarketDayForTheLast14Days(targetDate);
        String marketDayListString = marketDays.stream()
                .map(ld -> ld.format(YYYYMMDD))
                .collect(Collectors.joining(","));

        String date = TIMESTAMP_FMT.format(new Date());

        JobParameters kospiParams = new JobParametersBuilder()
                .addString("date", date)
                .addString("targetDate", targetDate)
                .addString("apiUrl", krxApiProperties.getKospiInfoUrl())
                .addString("apiKey", krxApiProperties.getKey())
                .addString("mktNm", "KOSPI")
                .addString("marketDayList", marketDayListString)
                .toJobParameters();

        JobParameters kosdaqParams = new JobParametersBuilder()
                .addString("date", date)
                .addString("targetDate", targetDate)
                .addString("apiUrl", krxApiProperties.getKosdaqInfoUrl())
                .addString("apiKey", krxApiProperties.getKey())
                .addString("mktNm", "KOSDAQ")
                .addString("marketDayList", marketDayListString)
                .toJobParameters();

        CompletableFuture<Void> kospi = asyncJobLauncher.runKospiRSICalculationJob(kospiParams);
        CompletableFuture<Void> kosdaq = asyncJobLauncher.runKosdaqRSICalculationJob(kosdaqParams);

        CompletableFuture.allOf(kospi, kosdaq).get();
    }
}
