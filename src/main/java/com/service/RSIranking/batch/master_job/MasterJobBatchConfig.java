package com.service.RSIranking.batch.master_job;

import com.service.RSIranking.batch.master_job.listener.MasterJobExecutionListener;
import com.service.RSIranking.batch.master_job.step.RSICalculationJobStepTasklet;
import com.service.RSIranking.batch.master_job.step.StockUpdateJobStepTasklet;
import com.service.RSIranking.batch.master_job.step.TradingInfoJobStepTasklet;
import com.service.RSIranking.batch.measurement.StepExecutionTimeListener;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * 마스터 배치 Job 설정 클래스.
 *
 * <p>모든 배치 Job을 순차적으로 실행하는 마스터 파이프라인 Job을 정의합니다.</p>
 *
 * <h2>Job 구성</h2>
 * <pre>
 * masterPipelineJob
 *   ├── Step 1: stockUpdateStep (종목 정보 수집)
 *   │     └── KOSPI/KOSDAQ 병렬 실행
 *   ├── Step 2: tradingInfoUpdateStep (일별 매매 정보 수집)
 *   │     └── KOSPI/KOSDAQ 병렬 실행
 *   └── Step 3: rsiCalculationStep (RSI 계산)
 *         └── KOSPI/KOSDAQ 병렬 실행
 * </pre>
 *
 * <h2>실패 처리</h2>
 * <ul>
 *   <li>각 단계에서 KOSPI 또는 KOSDAQ 중 하나라도 실패하면 전체 파이프라인 중단</li>
 *   <li>실패 시 FAILED 상태로 종료</li>
 * </ul>
 *
 * @author RSIranking Team
 * @version 1.0
 */
@Configuration
public class MasterJobBatchConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager platformTransactionManager;
    private final MasterJobExecutionListener masterJobExecutionListener;
    private final StepExecutionTimeListener stepExecutionTimeListener;

    private final StockUpdateJobStepTasklet stockUpdateJobStepTasklet;
    private final TradingInfoJobStepTasklet tradingInfoJobStepTasklet;
    private final RSICalculationJobStepTasklet rsiCalculationJobStepTasklet;

    public MasterJobBatchConfig(
            JobRepository jobRepository,
            @Qualifier("metaTransactionManager") PlatformTransactionManager platformTransactionManager,
            MasterJobExecutionListener masterJobExecutionListener,
            StepExecutionTimeListener stepExecutionTimeListener,
            StockUpdateJobStepTasklet stockUpdateJobStepTasklet,
            TradingInfoJobStepTasklet tradingInfoJobStepTasklet,
            RSICalculationJobStepTasklet rsiCalculationJobStepTasklet) {
        this.jobRepository = jobRepository;
        this.platformTransactionManager = platformTransactionManager;
        this.masterJobExecutionListener = masterJobExecutionListener;
        this.stepExecutionTimeListener = stepExecutionTimeListener;
        this.stockUpdateJobStepTasklet = stockUpdateJobStepTasklet;
        this.tradingInfoJobStepTasklet = tradingInfoJobStepTasklet;
        this.rsiCalculationJobStepTasklet = rsiCalculationJobStepTasklet;
    }

    // ====================================JOB=================================================

    /**
     * 마스터 파이프라인 Job을 정의합니다.
     *
     * <p>종목 정보 수집 -> 매매 정보 수집 -> RSI 계산 순서로 실행됩니다.</p>
     *
     * <p>각 Step은 KOSPI와 KOSDAQ를 병렬로 처리하며,
     * 하나라도 실패하면 전체 파이프라인이 중단됩니다.</p>
     *
     * @return 마스터 파이프라인 Job
     */
    @Bean
    public Job masterPipelineJob() {
        return new JobBuilder("masterPipelineJob", jobRepository)
                .listener(masterJobExecutionListener)
                .start(stockUpdateStep())
                    .on("FAILED").fail()
                .from(stockUpdateStep())
                    .on("*").to(tradingInfoUpdateStep())
                .from(tradingInfoUpdateStep())
                    .on("FAILED").fail()
                .from(tradingInfoUpdateStep())
                    .on("*").to(rsiCalculationStep())
                .from(rsiCalculationStep())
                    .on("FAILED").fail()
                .from(rsiCalculationStep())
                    .on("*").end()
                .end()
                .build();
    }

    // ===============================STEP 1===============================================

    /**
     * 종목 정보 수집 Step을 정의합니다.
     *
     * <p>KOSPI/KOSDAQ 종목 정보 업데이트 Job을 병렬 실행 후 결과를 대기합니다.</p>
     *
     * @return 종목 정보 수집 Step
     */
    @Bean
    public Step stockUpdateStep() {
        return new StepBuilder("stockUpdateStep", jobRepository)
                .tasklet(stockUpdateJobStepTasklet, platformTransactionManager)
                .listener(stockUpdateJobStepTasklet)
                .listener(stepExecutionTimeListener)
                .build();
    }

    // ===============================STEP 2===============================================

    /**
     * 일별 매매 정보 수집 Step을 정의합니다.
     *
     * <p>KOSPI/KOSDAQ 일별 매매 정보 업데이트 Job을 병렬 실행 후 결과를 대기합니다.</p>
     *
     * @return 일별 매매 정보 수집 Step
     */
    @Bean
    public Step tradingInfoUpdateStep() {
        return new StepBuilder("tradingInfoUpdateStep", jobRepository)
                .tasklet(tradingInfoJobStepTasklet, platformTransactionManager)
                .listener(tradingInfoJobStepTasklet)
                .listener(stepExecutionTimeListener)
                .build();
    }

    // ===============================STEP 3===============================================

    /**
     * RSI 계산 Step을 정의합니다.
     *
     * <p>KOSPI/KOSDAQ RSI 계산 Job을 병렬 실행 후 결과를 대기합니다.</p>
     *
     * @return RSI 계산 Step
     */
    @Bean
    public Step rsiCalculationStep() {
        return new StepBuilder("rsiCalculationStep", jobRepository)
                .tasklet(rsiCalculationJobStepTasklet, platformTransactionManager)
                .listener(rsiCalculationJobStepTasklet)
                .listener(stepExecutionTimeListener)
                .build();
    }
}
