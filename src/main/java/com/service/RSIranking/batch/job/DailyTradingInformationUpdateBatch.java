package com.service.RSIranking.batch.job;

import com.service.RSIranking.batch.measurement.JobExecutionTimeListener;
import com.service.RSIranking.batch.measurement.StepExecutionTimeListener;
import com.service.RSIranking.batch.step.RequestDailyTradingInfoTasklet;
import com.service.RSIranking.service.InterStepDataSharingWithRedisService;
import com.service.RSIranking.service.KrxRequestService;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.listener.ExecutionContextPromotionListener;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class DailyTradingInformationUpdateBatch {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager platformTransactionManager;
    private final JobExecutionTimeListener jobExecutionTimeListener;
    private final StepExecutionTimeListener stepExecutionTimeListener;
    private final KrxRequestService krxRequestService;
    private final InterStepDataSharingWithRedisService interStepDataSharingWithRedisService;

    public DailyTradingInformationUpdateBatch(JobRepository jobRepository,
                                              @Qualifier("metaTransactionManager") PlatformTransactionManager platformTransactionManager,
                                              JobExecutionTimeListener jobExecutionTimeListener,
                                              StepExecutionTimeListener stepExecutionTimeListener,
                                              KrxRequestService krxRequestService,
                                              InterStepDataSharingWithRedisService interStepDataSharingWithRedisService)
    {
        this.jobRepository =  jobRepository;
        this.platformTransactionManager = platformTransactionManager;
        this.jobExecutionTimeListener = jobExecutionTimeListener;
        this.stepExecutionTimeListener = stepExecutionTimeListener;
        this.krxRequestService = krxRequestService;
        this.interStepDataSharingWithRedisService = interStepDataSharingWithRedisService;
    }

    // todo 일별 매매 정도 업데이트 job
    @Bean
    public Job DailyTradingInformationUpdateJob() {
        return new JobBuilder("dailyTradingInformationUpdateJob", jobRepository)
                .listener(jobExecutionTimeListener)
                .start(requestDailyTradingInfoStep())
                .build();
    }
    // todo 일별 매매 정보 가져오는 step
    @Bean
    public Step requestDailyTradingInfoStep() {
        return new StepBuilder("requestKRXAPITradingStep", jobRepository)
                .tasklet( requestDailyTradingInfoTasklet(), platformTransactionManager)
                .listener(requestDailyTradingInfoTasklet())
                .listener(requestDailyTradingInfoListener() )
                .listener(stepExecutionTimeListener)
                .build();
    }
    @Bean
    public Tasklet requestDailyTradingInfoTasklet() {
        return new RequestDailyTradingInfoTasklet(krxRequestService, interStepDataSharingWithRedisService);
    }
    @Bean
    public ExecutionContextPromotionListener requestDailyTradingInfoListener() {
        ExecutionContextPromotionListener listener = new ExecutionContextPromotionListener();
        listener.setKeys(new String[] {"DailyTradingInfo"});
        return listener;
    }
    
    // todo 일별 매맴 정보 DB에 저장 step
}
