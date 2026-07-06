package com.service.RSIranking.batch.tranding_info_job;

import com.service.RSIranking.batch.measurement.JobExecutionTimeListener;
import com.service.RSIranking.batch.measurement.StepExecutionTimeListener;
import com.service.RSIranking.batch.tranding_info_job.step.RequestDailyTradingInfoTasklet;
import com.service.RSIranking.batch.tranding_info_job.step.UpdateDailyTradingInfoTasklet;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.listener.ExecutionContextPromotionListener;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * 일별 매매 정보 업데이트 배치 Job 설정 클래스.
 *
 * <p>KRX API를 통해 일별 매매 정보(종가, 거래량 등)를 수집하고
 * 데이터베이스에 저장하는 배치 작업을 구성합니다.</p>
 *
 * <h2>Job 구성</h2>
 * <pre>
 * DailyTradingInformationUpdateJob
 *   ├── Step 1: requestDailyTradingInfoStep (KRX API 요청)
 *   │     └── Tasklet: RequestDailyTradingInfoTasklet
 *   └── Step 2: updateDailyTradingInfoStep (DB 저장)
 *         └── Tasklet: UpdateDailyTradingInfoTasklet
 * </pre>
 *
 * <h2>수집 데이터</h2>
 * <ul>
 *   <li>종가 (tddClsprc)</li>
 *   <li>대비 (cmpprevddPrc)</li>
 *   <li>등락률 (flucRt)</li>
 *   <li>시가/고가/저가</li>
 *   <li>거래량/거래대금</li>
 * </ul>
 *
 * @author RSIranking Team
 * @version 1.0
 */
@Configuration
public class DailyTradingInformationUpdateBatch {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager platformTransactionManager;
    private final JobExecutionTimeListener jobExecutionTimeListener;
    private final StepExecutionTimeListener stepExecutionTimeListener;

    private final RequestDailyTradingInfoTasklet requestDailyTradingInfoTasklet;
    private final UpdateDailyTradingInfoTasklet updateDailyTradingInfoTasklet;


    public DailyTradingInformationUpdateBatch(JobRepository jobRepository,
                                              @Qualifier("metaTransactionManager") PlatformTransactionManager platformTransactionManager,
                                              JobExecutionTimeListener jobExecutionTimeListener,
                                              StepExecutionTimeListener stepExecutionTimeListener,
                                              RequestDailyTradingInfoTasklet requestDailyTradingInfoTasklet,
                                              UpdateDailyTradingInfoTasklet updateDailyTradingInfoTasklet)
    {
        this.jobRepository =  jobRepository;
        this.platformTransactionManager = platformTransactionManager;
        this.jobExecutionTimeListener = jobExecutionTimeListener;
        this.stepExecutionTimeListener = stepExecutionTimeListener;

        this.requestDailyTradingInfoTasklet = requestDailyTradingInfoTasklet;
        this.updateDailyTradingInfoTasklet = updateDailyTradingInfoTasklet;

    }

    /**
     * 일별 매매 정보 업데이트 Job을 정의합니다.
     *
     * @return 일별 매매 정보 업데이트 Job
     */
    @Bean
    public Job DailyTradingInformationUpdateJob() {
        return new JobBuilder("dailyTradingInformationUpdateJob", jobRepository)
                .listener(jobExecutionTimeListener)// 잡 리스너 실행 시간 측정
                .start(requestDailyTradingInfoStep())
                .on("NO_DATA").end() // 데이터가 없으면 잡 종료
                .on("REDIS_FAILED").end()// 레디스 저장 실패 시 잡 종료
                .from(requestDailyTradingInfoStep())
                .on("*").to(updateDailyTradingInfoStep())
                .end()
                .build();
    }

    //=============================STEP1================================================

    /**
     * KRX API에서 일별 매매 정보를 가져오는 Step을 정의합니다.
     *
     * @return 일별 매매 정보 요청 Step
     */
    @Bean
    public Step requestDailyTradingInfoStep() {
        return new StepBuilder("requestKRXAPITradingStep", jobRepository)
                .tasklet( requestDailyTradingInfoTasklet, platformTransactionManager) // 일별 데이터 요청
                .listener(requestDailyTradingInfoTasklet)
                .listener(requestDailyTradingInfoListener() )
                .listener(stepExecutionTimeListener) // 스탭 리스너 실행 시간 측정
                .build();
    }
//    @Bean
//    public Tasklet requestDailyTradingInfoTasklet() {
//        return new RequestDailyTradingInfoTasklet(krxRequestService, interStepDataSharingWithRedisService);
//    }
    @Bean
    public ExecutionContextPromotionListener requestDailyTradingInfoListener() {
        ExecutionContextPromotionListener listener = new ExecutionContextPromotionListener();
        listener.setKeys(new String[] {"DailyTradingInfo"});
        return listener;
    }

    //=============================STEP2================================================

    /**
     * 일별 매매 정보를 데이터베이스에 저장하는 Step을 정의합니다.
     *
     * @return 일별 매매 정보 저장 Step
     */
    @Bean
    public Step updateDailyTradingInfoStep() {
        return new StepBuilder("updateKRXAPITradingStep", jobRepository)
                .tasklet( updateDailyTradingInfoTasklet, platformTransactionManager)
                .listener(updateDailyTradingInfoTasklet)
                .listener(stepExecutionTimeListener)
                .build();
    }
//    @Bean
//    public Tasklet udateDailyTradingInfoTasklet() {
//        return new UpdateDailyTradingInfoTasklet(interStepDataSharingWithRedisService, updateDailyTradingInfoService);
//    }
}
