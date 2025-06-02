package com.service.RSIranking.batch.tranding_info_job;

import com.service.RSIranking.batch.measurement.JobExecutionTimeListener;
import com.service.RSIranking.batch.measurement.StepExecutionTimeListener;
import com.service.RSIranking.batch.tranding_info_job.step.RequestDailyTradingInfoTasklet;
import com.service.RSIranking.batch.tranding_info_job.step.UpdateDailyTradingInfoTasklet;
import com.service.RSIranking.service.InterStepDataSharingWithRedisService;
import com.service.RSIranking.service.KrxRequestService;
import com.service.RSIranking.service.UpdateDailyTradingInfoService;
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
    private final UpdateDailyTradingInfoService updateDailyTradingInfoService;


    public DailyTradingInformationUpdateBatch(JobRepository jobRepository,
                                              @Qualifier("metaTransactionManager") PlatformTransactionManager platformTransactionManager,
                                              JobExecutionTimeListener jobExecutionTimeListener,
                                              StepExecutionTimeListener stepExecutionTimeListener,
                                              KrxRequestService krxRequestService,
                                              InterStepDataSharingWithRedisService interStepDataSharingWithRedisService,
                                              UpdateDailyTradingInfoService updateDailyTradingInfoService)
    {
        this.jobRepository =  jobRepository;
        this.platformTransactionManager = platformTransactionManager;
        this.jobExecutionTimeListener = jobExecutionTimeListener;
        this.stepExecutionTimeListener = stepExecutionTimeListener;
        this.krxRequestService = krxRequestService;
        this.interStepDataSharingWithRedisService = interStepDataSharingWithRedisService;
        this.updateDailyTradingInfoService = updateDailyTradingInfoService;

    }

    // todo 일별 매매 정도 업데이트 job
    @Bean
    public Job DailyTradingInformationUpdateJob() {
        return new JobBuilder("dailyTradingInformationUpdateJob", jobRepository)
                .listener(jobExecutionTimeListener)
                .start(requestDailyTradingInfoStep())
                .on("NO_DATA").end() // 데이터가 없으면 잡 종료
                .on("REDIS_FAILED").end()// 레디스 저장 실패 시 잡 종료
                .from(requestDailyTradingInfoStep())
                .on("*").to(updateDailyTradingInfoStep())
                .end()
                .build();
    }

    //=============================STEP1================================================

    // 일별 매매 정보 가져오는 step
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

    //=============================STEP2================================================

    // todo 일별 매맴 정보 DB에 저장 step
    @Bean
    public Step updateDailyTradingInfoStep() {
        return new StepBuilder("updateKRXAPITradingStep", jobRepository)
                .tasklet( udateDailyTradingInfoTasklet(), platformTransactionManager)
                .listener(udateDailyTradingInfoTasklet())
                .listener(stepExecutionTimeListener)
                .build();
    }
    @Bean
    public Tasklet udateDailyTradingInfoTasklet() {
        return new UpdateDailyTradingInfoTasklet(interStepDataSharingWithRedisService, updateDailyTradingInfoService);
    }
}
