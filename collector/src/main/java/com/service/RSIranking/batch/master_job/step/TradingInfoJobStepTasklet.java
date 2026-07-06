package com.service.RSIranking.batch.master_job.step;

import com.service.RSIranking.config.krx_api.KrxApiProperties;
import com.service.RSIranking.schedule.AsyncJobLauncher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.*;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * 일별 매매 정보 업데이트 Job을 실행하는 Tasklet.
 *
 * <p>마스터 Job의 두 번째 Step으로, KOSPI와 KOSDAQ 일별 매매 정보 업데이트 Job을
 * 병렬로 실행하고 두 Job이 모두 완료될 때까지 대기합니다.</p>
 *
 * <p>{@link StepScope}로 동작하며, JobParameters는 SpEL로 직접 주입됩니다.
 * 인스턴스 필드 race가 없습니다.</p>
 *
 * <h2>실행 흐름</h2>
 * <ol>
 *   <li>SpEL로 주입된 yesterday 사용</li>
 *   <li>KOSPI/KOSDAQ Job 파라미터 생성 (UUID 포함)</li>
 *   <li>AsyncJobLauncher를 통해 병렬 실행</li>
 *   <li>CompletableFuture.allOf()로 완료 대기</li>
 *   <li>실패 시 예외 발생으로 마스터 Job 중단</li>
 * </ol>
 *
 * @author RSIranking Team
 * @version 1.1
 * @see AsyncJobLauncher
 */
@Component
@StepScope
@Slf4j
public class TradingInfoJobStepTasklet implements Tasklet, StepExecutionListener {

    private final AsyncJobLauncher asyncJobLauncher;
    private final KrxApiProperties krxApiProperties;
    private final String yesterday;

    public TradingInfoJobStepTasklet(
            AsyncJobLauncher asyncJobLauncher,
            KrxApiProperties krxApiProperties,
            @Value("#{jobParameters['yesterday']}") String yesterday) {
        this.asyncJobLauncher = asyncJobLauncher;
        this.krxApiProperties = krxApiProperties;
        this.yesterday = yesterday;
    }

    /**
     * Step 실행 전 시작 로그를 출력합니다.
     *
     * @param stepExecution Step 실행 정보
     */
    @Override
    public void beforeStep(StepExecution stepExecution) {
        log.info("[Step 2/3] 일별 매매 정보 업데이트 시작 - 대상일: {}", yesterday);
    }

    /**
     * KOSPI/KOSDAQ 일별 매매 정보 업데이트 Job을 병렬로 실행합니다.
     *
     * @param contribution Step 기여 정보
     * @param chunkContext 청크 컨텍스트
     * @return RepeatStatus.FINISHED
     * @throws Exception Job 실행 실패 시
     */
    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {

        String date = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss").format(new Date());

        JobParameters kospiJobParameters = new JobParametersBuilder()
                .addString("uuid", UUID.randomUUID().toString())
                .addString("date", date)
                .addString("apiUrl", krxApiProperties.getKospiTradingInfoUrl())
                .addString("apiKey", krxApiProperties.getKey())
                .addString("mktNm", "KOSPI")
                .addString("yesterday", yesterday)
                .toJobParameters();

        JobParameters kosdaqJobParameters = new JobParametersBuilder()
                .addString("uuid", UUID.randomUUID().toString())
                .addString("date", date)
                .addString("apiUrl", krxApiProperties.getKosdaqTradingInfoUrl())
                .addString("apiKey", krxApiProperties.getKey())
                .addString("mktNm", "KOSDAQ")
                .addString("yesterday", yesterday)
                .toJobParameters();

        CompletableFuture<Void> kospiFuture = asyncJobLauncher.runKospiTradingJob(kospiJobParameters);
        CompletableFuture<Void> kosdaqFuture = asyncJobLauncher.runKosdaqTradingJob(kosdaqJobParameters);

        try {
            CompletableFuture.allOf(kospiFuture, kosdaqFuture).get();

            boolean kospiSuccess = !kospiFuture.isCompletedExceptionally();
            boolean kosdaqSuccess = !kosdaqFuture.isCompletedExceptionally();

            log.info("KOSPI Trading Job: {}", kospiSuccess ? "성공" : "실패");
            log.info("KOSDAQ Trading Job: {}", kosdaqSuccess ? "성공" : "실패");

            if (!kospiSuccess || !kosdaqSuccess) {
                throw new RuntimeException("일별 매매 정보 업데이트 Job 실패: KOSPI=" + kospiSuccess + ", KOSDAQ=" + kosdaqSuccess);
            }

        } catch (ExecutionException | InterruptedException e) {
            log.error("일별 매매 정보 업데이트 중 오류 발생", e);
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
        log.info("[Step 2/3] 일별 매매 정보 업데이트 완료 - 상태: {}", stepExecution.getExitStatus());
        return stepExecution.getExitStatus();
    }
}
