package com.service.RSIranking.batch.master_job.step;

import com.service.RSIranking.config.krx_api.KrxApiProperties;
import com.service.RSIranking.schedule.AsyncJobLauncher;
import com.service.RSIranking.util.MarketDayForTheLast14Days;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.*;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

/**
 * RSI 계산 Job을 실행하는 Tasklet.
 *
 * <p>마스터 Job의 세 번째 Step으로, KOSPI와 KOSDAQ RSI 계산 Job을
 * 병렬로 실행하고 두 Job이 모두 완료될 때까지 대기합니다.</p>
 *
 * <p>{@link StepScope}로 동작하며, JobParameters는 SpEL로 직접 주입됩니다.
 * 인스턴스 필드 race가 없습니다.</p>
 *
 * <h2>실행 흐름</h2>
 * <ol>
 *   <li>SpEL로 주입된 yesterday 사용</li>
 *   <li>RSI 계산을 위한 최근 14일 영업일 목록 조회</li>
 *   <li>KOSPI/KOSDAQ Job 파라미터 생성 (영업일 목록 포함)</li>
 *   <li>AsyncJobLauncher를 통해 병렬 실행</li>
 *   <li>CompletableFuture.allOf()로 완료 대기</li>
 *   <li>실패 시 예외 발생으로 마스터 Job 중단</li>
 * </ol>
 *
 * @author RSIranking Team
 * @version 1.1
 * @see AsyncJobLauncher
 * @see MarketDayForTheLast14Days
 */
@Component
@StepScope
@Slf4j
public class RSICalculationJobStepTasklet implements Tasklet, StepExecutionListener {

    private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final AsyncJobLauncher asyncJobLauncher;
    private final KrxApiProperties krxApiProperties;
    private final MarketDayForTheLast14Days marketDayForTheLast14Days;
    private final String yesterday;

    public RSICalculationJobStepTasklet(
            AsyncJobLauncher asyncJobLauncher,
            KrxApiProperties krxApiProperties,
            MarketDayForTheLast14Days marketDayForTheLast14Days,
            @Value("#{jobParameters['yesterday']}") String yesterday) {
        this.asyncJobLauncher = asyncJobLauncher;
        this.krxApiProperties = krxApiProperties;
        this.marketDayForTheLast14Days = marketDayForTheLast14Days;
        this.yesterday = yesterday;
    }

    /**
     * Step 실행 전 시작 로그를 출력합니다.
     *
     * @param stepExecution Step 실행 정보
     */
    @Override
    public void beforeStep(StepExecution stepExecution) {
        log.info("[Step 3/3] RSI 계산 시작 - 대상일: {}", yesterday);
    }

    /**
     * KOSPI/KOSDAQ RSI 계산 Job을 병렬로 실행합니다.
     *
     * @param contribution Step 기여 정보
     * @param chunkContext 청크 컨텍스트
     * @return RepeatStatus.FINISHED
     * @throws Exception Job 실행 실패 시
     */
    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {

        String date = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss").format(new Date());

        // RSI 계산을 위한 최근 14일 영업일 목록 조회 (execute() 안에서 매 실행마다 산출)
        List<LocalDate> marketDays = marketDayForTheLast14Days.getMarketDayForTheLast14Days(yesterday);
        String marketDayListString = marketDays.stream()
                .map(ld -> ld.format(YYYYMMDD))
                .collect(Collectors.joining(","));

        log.info("[Step 3/3] RSI 영업일 수: {}", marketDays.size());

        JobParameters kospiJobParameters = new JobParametersBuilder()
                .addString("date", date)
                .addString("targetDate", yesterday)
                .addString("apiUrl", krxApiProperties.getKospiInfoUrl())
                .addString("apiKey", krxApiProperties.getKey())
                .addString("mktNm", "KOSPI")
                .addString("marketDayList", marketDayListString)
                .toJobParameters();

        JobParameters kosdaqJobParameters = new JobParametersBuilder()
                .addString("date", date)
                .addString("targetDate", yesterday)
                .addString("apiUrl", krxApiProperties.getKosdaqInfoUrl())
                .addString("apiKey", krxApiProperties.getKey())
                .addString("mktNm", "KOSDAQ")
                .addString("marketDayList", marketDayListString)
                .toJobParameters();

        JobParameters etfJobParameters = new JobParametersBuilder()
                .addString("date", date)
                .addString("targetDate", yesterday)
                .addString("apiUrl", krxApiProperties.getEtfInfoUrl())
                .addString("apiKey", krxApiProperties.getKey())
                .addString("mktNm", "ETF")
                .addString("marketDayList", marketDayListString)
                .toJobParameters();

        CompletableFuture<Void> kospiFuture = asyncJobLauncher.runKospiRSICalculationJob(kospiJobParameters);
        CompletableFuture<Void> kosdaqFuture = asyncJobLauncher.runKosdaqRSICalculationJob(kosdaqJobParameters);
        CompletableFuture<Void> etfFuture = asyncJobLauncher.runEtfRSICalculationJob(etfJobParameters);

        try {
            CompletableFuture.allOf(kospiFuture, kosdaqFuture, etfFuture).get();

            boolean kospiSuccess = !kospiFuture.isCompletedExceptionally();
            boolean kosdaqSuccess = !kosdaqFuture.isCompletedExceptionally();
            boolean etfSuccess = !etfFuture.isCompletedExceptionally();

            log.info("KOSPI RSI Job: {}", kospiSuccess ? "성공" : "실패");
            log.info("KOSDAQ RSI Job: {}", kosdaqSuccess ? "성공" : "실패");
            log.info("ETF RSI Job: {}", etfSuccess ? "성공" : "실패");

            if (!kospiSuccess || !kosdaqSuccess || !etfSuccess) {
                throw new RuntimeException("RSI 계산 Job 실패: KOSPI=" + kospiSuccess + ", KOSDAQ=" + kosdaqSuccess + ", ETF=" + etfSuccess);
            }

        } catch (ExecutionException | InterruptedException e) {
            log.error("RSI 계산 중 오류 발생", e);
            throw e;
        }

        return RepeatStatus.FINISHED;
    }

    /**
     * Step 완료 후 결과를 로깅합니다.
     *
     * @param stepExecution Step 실행 정보
     * @return Step 종료 상태
     */
    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        log.info("[Step 3/3] RSI 계산 완료 - 상태: {}", stepExecution.getExitStatus());
        return stepExecution.getExitStatus();
    }
}
