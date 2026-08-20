package com.service.RSIranking.schedule;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.configuration.JobRegistry;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * 비동기 배치 작업 런처.
 *
 * <p>Spring Batch 작업을 비동기로 실행하기 위한 서비스입니다.
 * 각 배치 작업을 별도의 스레드에서 실행하여 병렬 처리가 가능하도록 합니다.</p>
 *
 * <p>지원하는 비동기 작업:</p>
 * <ul>
 *   <li>종목 정보 업데이트 (KOSPI/KOSDAQ)</li>
 *   <li>일별 매매 정보 업데이트 (KOSPI/KOSDAQ)</li>
 *   <li>RSI 지표 계산 (KOSPI/KOSDAQ)</li>
 * </ul>
 *
 * <p>모든 메서드는 {@code @Async("asyncExecutor")} 어노테이션을 사용하여
 * 비동기 실행되며, {@link CompletableFuture}를 반환합니다.</p>
 *
 * <p>병목 진단을 위해 진입/종료 시점에 스레드 이름과 nanoTime을 로깅합니다.</p>
 *
 * <p>자식 Job이 {@link BatchStatus#COMPLETED}가 아닌 상태로 종료되면
 * {@link CompletableFuture#failedFuture(Throwable)}를 반환해 호출자에게 실패를 명확히 전달합니다.</p>
 *
 * @author RSIranking Team
 * @version 1.2
 * @see org.springframework.batch.core.launch.JobLauncher
 * @see org.springframework.batch.core.configuration.JobRegistry
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AsyncJobLauncher {

    private final JobLauncher jobLauncher;
    private final JobRegistry jobRegistry;

    /**
     * 자식 Job 1회 실행을 위한 공통 흐름.
     *
     * <p>진입/종료 로깅, JobExecution 상태 검사, 예외 처리를 일괄 수행합니다.</p>
     *
     * @param tag      로그용 태그 (예: "KOSPI-Stock")
     * @param jobName  실행할 Job 이름 (JobRegistry에서 lookup)
     * @param paramsSupplier JobParameters 공급자
     * @return Job 실행 결과를 담은 CompletableFuture (COMPLETED가 아니면 failedFuture)
     */
    private CompletableFuture<Void> runJob(String tag, String jobName, Supplier<JobParameters> paramsSupplier) {
        String thread = Thread.currentThread().getName();
        long startNs = System.nanoTime();
        log.info("[ASYNC-JOB] {} 시작 [thread={}, t={}ns]", tag, thread, startNs);
        try {
            Job job = jobRegistry.getJob(jobName);
            JobExecution execution = jobLauncher.run(job, paramsSupplier.get());
            long endNs = System.nanoTime();
            long durationMs = (endNs - startNs) / 1_000_000L;
            BatchStatus status = execution.getStatus();
            log.info("[ASYNC-JOB] {} 종료 [thread={}, status={}, duration={}ms]",
                    tag, thread, status, durationMs);
            if (status != BatchStatus.COMPLETED) {
                String msg = String.format("자식 Job %s 비정상 종료: status=%s", tag, status);
                return CompletableFuture.failedFuture(new IllegalStateException(msg));
            }
            return CompletableFuture.completedFuture(null);
        } catch (Exception e) {
            long endNs = System.nanoTime();
            long durationMs = (endNs - startNs) / 1_000_000L;
            log.error("[ASYNC-JOB] {} 예외 [thread={}, duration={}ms]: {}",
                    tag, thread, durationMs, e.getMessage(), e);
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * KOSPI 종목 정보 업데이트 작업을 비동기로 실행합니다.
     *
     * @param parameters 작업 파라미터
     * @return 작업 완료를 나타내는 CompletableFuture
     */
    @Async("asyncExecutor")
    public CompletableFuture<Void> runKospiInfoJob(JobParameters parameters) {
        return runJob("KOSPI-Stock", "stockUpdateJob", () -> parameters);
    }

    /**
     * KOSDAQ 종목 정보 업데이트 작업을 비동기로 실행합니다.
     *
     * @param parameters 작업 파라미터
     * @return 작업 완료를 나타내는 CompletableFuture
     */
    @Async("asyncExecutor")
    public CompletableFuture<Void> runKosdaqInfoJob(JobParameters parameters) {
        return runJob("KOSDAQ-Stock", "stockUpdateJob", () -> parameters);
    }

    @Async("asyncExecutor")
    public CompletableFuture<Void> runEtfInfoJob(JobParameters parameters) {
        return runJob("ETF-Stock", "stockUpdateJob", () -> parameters);
    }

    /**
     * KOSPI 일별 매매 정보 업데이트 작업을 비동기로 실행합니다.
     *
     * @param parameters 작업 파라미터
     * @return 작업 완료를 나타내는 CompletableFuture
     */
    @Async("asyncExecutor")
    public CompletableFuture<Void> runKospiTradingJob(JobParameters parameters) {
        return runJob("KOSPI-Trading", "dailyTradingInformationUpdateJob", () -> parameters);
    }

    /**
     * KOSDAQ 일별 매매 정보 업데이트 작업을 비동기로 실행합니다.
     *
     * @param parameters 작업 파라미터
     * @return 작업 완료를 나타내는 CompletableFuture
     */
    @Async("asyncExecutor")
    public CompletableFuture<Void> runKosdaqTradingJob(JobParameters parameters) {
        return runJob("KOSDAQ-Trading", "dailyTradingInformationUpdateJob", () -> parameters);
    }

    @Async("asyncExecutor")
    public CompletableFuture<Void> runEtfTradingJob(JobParameters parameters) {
        return runJob("ETF-Trading", "dailyTradingInformationUpdateJob", () -> parameters);
    }

    /**
     * KOSPI RSI 지표 계산 작업을 비동기로 실행합니다.
     *
     * @param parameters 작업 파라미터
     * @return 작업 완료를 나타내는 CompletableFuture
     */
    @Async("asyncExecutor")
    public CompletableFuture<Void> runKospiRSICalculationJob(JobParameters parameters) {
        return runJob("KOSPI-RSI", "RSICalculationJob", () -> parameters);
    }

    /**
     * KOSDAQ RSI 지표 계산 작업을 비동기로 실행합니다.
     *
     * @param parameters 작업 파라미터
     * @return 작업 완료를 나타내는 CompletableFuture
     */
    @Async("asyncExecutor")
    public CompletableFuture<Void> runKosdaqRSICalculationJob(JobParameters parameters) {
        return runJob("KOSDAQ-RSI", "RSICalculationJob", () -> parameters);
    }

    @Async("asyncExecutor")
    public CompletableFuture<Void> runEtfRSICalculationJob(JobParameters parameters) {
        return runJob("ETF-RSI", "RSICalculationJob", () -> parameters);
    }
}
