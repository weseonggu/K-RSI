package com.service.RSIranking.batch.master_job.step;

import com.service.RSIranking.config.krx_api.KrxApiProperties;
import com.service.RSIranking.schedule.AsyncJobLauncher;
import com.service.RSIranking.util.MarketDayForTheLast14Days;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.*;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
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
 * <h2>실행 흐름</h2>
 * <ol>
 *   <li>마스터 Job에서 전달받은 파라미터(yesterday) 추출</li>
 *   <li>RSI 계산을 위한 최근 14일 영업일 목록 조회</li>
 *   <li>KOSPI/KOSDAQ Job 파라미터 생성 (영업일 목록 포함)</li>
 *   <li>AsyncJobLauncher를 통해 병렬 실행</li>
 *   <li>CompletableFuture.allOf()로 완료 대기</li>
 *   <li>실패 시 예외 발생으로 마스터 Job 중단</li>
 * </ol>
 *
 * @author RSIranking Team
 * @version 1.0
 * @see AsyncJobLauncher
 * @see MarketDayForTheLast14Days
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RSICalculationJobStepTasklet implements Tasklet, StepExecutionListener {

    private final AsyncJobLauncher asyncJobLauncher;
    private final KrxApiProperties krxApiProperties;
    private final MarketDayForTheLast14Days marketDayForTheLast14Days;

    private String yesterday;
    private String date;
    private String marketDayListString;

    /**
     * Step 실행 전 마스터 Job의 파라미터를 추출하고 영업일 목록을 조회합니다.
     *
     * @param stepExecution Step 실행 정보
     */
    @Override
    public void beforeStep(StepExecution stepExecution) {
        JobParameters jobParameters = stepExecution.getJobExecution().getJobParameters();
        this.yesterday = jobParameters.getString("yesterday");

        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss");
        this.date = dateFormat.format(new Date());

        // RSI 계산을 위한 최근 14일 영업일 목록 조회
        List<LocalDate> marketDays = marketDayForTheLast14Days.getMarketDayForTheLast14Days(yesterday);
        this.marketDayListString = marketDays.stream()
                .map(ld -> ld.format(DateTimeFormatter.ofPattern("yyyyMMdd")))
                .collect(Collectors.joining(","));

        log.info("[Step 3/3] RSI 계산 시작 - 대상일: {}, 영업일 수: {}", yesterday, marketDays.size());
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

        CompletableFuture<Void> kospiFuture = asyncJobLauncher.runKospiRSICalculationJob(kospiJobParameters);
        CompletableFuture<Void> kosdaqFuture = asyncJobLauncher.runKosdaqRSICalculationJob(kosdaqJobParameters);

        try {
            CompletableFuture.allOf(kospiFuture, kosdaqFuture).get();

            boolean kospiSuccess = !kospiFuture.isCompletedExceptionally();
            boolean kosdaqSuccess = !kosdaqFuture.isCompletedExceptionally();

            log.info("KOSPI RSI Job: {}", kospiSuccess ? "성공" : "실패");
            log.info("KOSDAQ RSI Job: {}", kosdaqSuccess ? "성공" : "실패");

            if (!kospiSuccess || !kosdaqSuccess) {
                contribution.setExitStatus(ExitStatus.FAILED);
                throw new RuntimeException("RSI 계산 Job 실패: KOSPI=" + kospiSuccess + ", KOSDAQ=" + kosdaqSuccess);
            }

        } catch (ExecutionException | InterruptedException e) {
            log.error("RSI 계산 중 오류 발생", e);
            contribution.setExitStatus(ExitStatus.FAILED);
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
