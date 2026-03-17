package com.service.RSIranking.schedule;

import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.configuration.JobRegistry;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

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
 * @author RSIranking Team
 * @version 1.1
 * @see org.springframework.batch.core.launch.JobLauncher
 * @see org.springframework.batch.core.configuration.JobRegistry
 */
@Service
@RequiredArgsConstructor
public class AsyncJobLauncher {

    private final JobLauncher jobLauncher;
    private final JobRegistry jobRegistry;

    /**
     * KOSPI 종목 정보 업데이트 작업을 비동기로 실행합니다.
     *
     * @param parameters 작업 파라미터
     * @return 작업 완료를 나타내는 CompletableFuture
     */
    @Async("asyncExecutor")
    public CompletableFuture<Void> runKospiInfoJob(JobParameters parameters) {
        try {
            jobLauncher.run(jobRegistry.getJob("stockUpdateJob"), parameters);
            return CompletableFuture.completedFuture(null);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * KOSDAQ 종목 정보 업데이트 작업을 비동기로 실행합니다.
     *
     * @param parameters 작업 파라미터
     * @return 작업 완료를 나타내는 CompletableFuture
     */
    @Async("asyncExecutor")
    public CompletableFuture<Void> runKosdaqInfoJob(JobParameters parameters) {
        try {
            jobLauncher.run(jobRegistry.getJob("stockUpdateJob"), parameters);
            return CompletableFuture.completedFuture(null);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * KOSPI 일별 매매 정보 업데이트 작업을 비동기로 실행합니다.
     *
     * @param parameters 작업 파라미터
     * @return 작업 완료를 나타내는 CompletableFuture
     */
    @Async("asyncExecutor")
    public CompletableFuture<Void> runKospiTradingJob(JobParameters parameters) {
        try {
            jobLauncher.run(jobRegistry.getJob("dailyTradingInformationUpdateJob"), parameters);
            return CompletableFuture.completedFuture(null);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * KOSDAQ 일별 매매 정보 업데이트 작업을 비동기로 실행합니다.
     *
     * @param parameters 작업 파라미터
     * @return 작업 완료를 나타내는 CompletableFuture
     */
    @Async("asyncExecutor")
    public CompletableFuture<Void> runKosdaqTradingJob(JobParameters parameters) {
        try {
            jobLauncher.run(jobRegistry.getJob("dailyTradingInformationUpdateJob"), parameters);
            return CompletableFuture.completedFuture(null);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * KOSPI RSI 지표 계산 작업을 비동기로 실행합니다.
     *
     * @param parameters 작업 파라미터
     * @return 작업 완료를 나타내는 CompletableFuture
     */
    @Async("asyncExecutor")
    public CompletableFuture<Void> runKospiRSICalculationJob(JobParameters parameters) {
        try {
            jobLauncher.run(jobRegistry.getJob("RSICalculationJob"), parameters);
            return CompletableFuture.completedFuture(null);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * KOSDAQ RSI 지표 계산 작업을 비동기로 실행합니다.
     *
     * @param parameters 작업 파라미터
     * @return 작업 완료를 나타내는 CompletableFuture
     */
    @Async("asyncExecutor")
    public CompletableFuture<Void> runKosdaqRSICalculationJob(JobParameters parameters) {
        try {
            jobLauncher.run(jobRegistry.getJob("RSICalculationJob"), parameters);
            return CompletableFuture.completedFuture(null);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }
}
