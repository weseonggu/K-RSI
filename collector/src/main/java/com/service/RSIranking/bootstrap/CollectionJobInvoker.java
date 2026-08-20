package com.service.RSIranking.bootstrap;

import com.service.RSIranking.config.krx_api.KrxApiProperties;
import com.service.RSIranking.schedule.AsyncJobLauncher;
import com.service.RSIranking.util.MarketDayForTheLast14Days;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * 특정 일자를 지정해 수집 Job을 호출하는 헬퍼 컴포넌트.
 *
 * <p>{@link AsyncJobLauncher}로 KOSPI/KOSDAQ Job을 병렬 실행하고
 * {@link CompletableFuture#allOf}로 완료를 대기합니다. 스케줄러 Launcher들이
 * "어제" 기준으로만 동작하는 것과 달리, 임의의 과거 일자를 대상으로 실행할 수 있어
 * 기동 시 캐치업({@link CatchupBootstrap})에서 사용합니다.</p>
 *
 * @author RSIranking Team
 * @version 1.0
 * @see CatchupBootstrap
 */
@Component
@RequiredArgsConstructor
public class CollectionJobInvoker {

    private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final AsyncJobLauncher asyncJobLauncher;
    private final KrxApiProperties krxApiProperties;
    private final MarketDayForTheLast14Days marketDayForTheLast14Days;

    /**
     * KOSPI/KOSDAQ 종목 정보 Job을 대상 일자 기준으로 병렬 실행하고 완료를 대기합니다.
     *
     * @param targetDate 대상 일자 (yyyyMMdd)
     * @throws Exception Job 실행 실패 시
     */
    public void launchStockInfoJob(String targetDate) throws Exception {
        String date = newTimestamp();

        JobParameters kospiParams = new JobParametersBuilder()
                .addString("date", date)
                .addString("apiUrl", krxApiProperties.getKospiInfoUrl())
                .addString("apiKey", krxApiProperties.getKey())
                .addString("mktNm", "KOSPI")
                .addString("yesterday", targetDate)
                .toJobParameters();

        JobParameters kosdaqParams = new JobParametersBuilder()
                .addString("date", date)
                .addString("apiUrl", krxApiProperties.getKosdaqInfoUrl())
                .addString("apiKey", krxApiProperties.getKey())
                .addString("mktNm", "KOSDAQ")
                .addString("yesterday", targetDate)
                .toJobParameters();

        JobParameters etfParams = new JobParametersBuilder()
                .addString("date", date)
                .addString("apiUrl", krxApiProperties.getEtfInfoUrl())
                .addString("apiKey", krxApiProperties.getKey())
                .addString("mktNm", "ETF")
                .addString("yesterday", targetDate)
                .toJobParameters();

        CompletableFuture<Void> kospi = asyncJobLauncher.runKospiInfoJob(kospiParams);
        CompletableFuture<Void> kosdaq = asyncJobLauncher.runKosdaqInfoJob(kosdaqParams);
        CompletableFuture<Void> etf = asyncJobLauncher.runEtfInfoJob(etfParams);

        CompletableFuture.allOf(kospi, kosdaq, etf).get();
    }

    /**
     * KOSPI/KOSDAQ 일별 매매 정보 Job을 대상 일자 기준으로 병렬 실행하고 완료를 대기합니다.
     *
     * @param targetDate 대상 일자 (yyyyMMdd)
     * @throws Exception Job 실행 실패 시
     */
    public void launchTradingInfoJob(String targetDate) throws Exception {
        String date = newTimestamp();

        JobParameters kospiParams = new JobParametersBuilder()
                .addString("uuid", UUID.randomUUID().toString())
                .addString("date", date)
                .addString("apiUrl", krxApiProperties.getKospiTradingInfoUrl())
                .addString("apiKey", krxApiProperties.getKey())
                .addString("mktNm", "KOSPI")
                .addString("yesterday", targetDate)
                .toJobParameters();

        JobParameters kosdaqParams = new JobParametersBuilder()
                .addString("uuid", UUID.randomUUID().toString())
                .addString("date", date)
                .addString("apiUrl", krxApiProperties.getKosdaqTradingInfoUrl())
                .addString("apiKey", krxApiProperties.getKey())
                .addString("mktNm", "KOSDAQ")
                .addString("yesterday", targetDate)
                .toJobParameters();

        JobParameters etfParams = new JobParametersBuilder()
                .addString("uuid", UUID.randomUUID().toString())
                .addString("date", date)
                .addString("apiUrl", krxApiProperties.getEtfTradingInfoUrl())
                .addString("apiKey", krxApiProperties.getKey())
                .addString("mktNm", "ETF")
                .addString("yesterday", targetDate)
                .toJobParameters();

        CompletableFuture<Void> kospi = asyncJobLauncher.runKospiTradingJob(kospiParams);
        CompletableFuture<Void> kosdaq = asyncJobLauncher.runKosdaqTradingJob(kosdaqParams);
        CompletableFuture<Void> etf = asyncJobLauncher.runEtfTradingJob(etfParams);

        CompletableFuture.allOf(kospi, kosdaq, etf).get();
    }

    /**
     * KOSPI/KOSDAQ RSI 계산 Job을 대상 일자 기준으로 병렬 실행하고 완료를 대기합니다.
     *
     * <p>targetDate 기준 과거 13 영업일 + targetDate = 14일치 lookback으로 RSI를 계산합니다.</p>
     *
     * @param targetDate 대상 일자 (yyyyMMdd)
     * @throws Exception Job 실행 실패 시
     */
    public void launchRsiCalculationJob(String targetDate) throws Exception {
        List<LocalDate> marketDays = marketDayForTheLast14Days.getMarketDayForTheLast14Days(targetDate);
        String marketDayListString = marketDays.stream()
                .map(ld -> ld.format(YYYYMMDD))
                .collect(Collectors.joining(","));

        String date = newTimestamp();

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

        JobParameters etfParams = new JobParametersBuilder()
                .addString("date", date)
                .addString("targetDate", targetDate)
                .addString("apiUrl", krxApiProperties.getEtfInfoUrl())
                .addString("apiKey", krxApiProperties.getKey())
                .addString("mktNm", "ETF")
                .addString("marketDayList", marketDayListString)
                .toJobParameters();

        CompletableFuture<Void> kospi = asyncJobLauncher.runKospiRSICalculationJob(kospiParams);
        CompletableFuture<Void> kosdaq = asyncJobLauncher.runKosdaqRSICalculationJob(kosdaqParams);
        CompletableFuture<Void> etf = asyncJobLauncher.runEtfRSICalculationJob(etfParams);

        CompletableFuture.allOf(kospi, kosdaq, etf).get();
    }

    /**
     * JobInstance 유니크 보장을 위한 실행 시각 문자열을 생성합니다.
     * ({@link SimpleDateFormat}은 스레드 안전하지 않아 호출마다 생성)
     */
    private static String newTimestamp() {
        return new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss").format(new Date());
    }
}
