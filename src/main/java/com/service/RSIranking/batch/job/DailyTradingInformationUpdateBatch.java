package com.service.RSIranking.batch.job;

import com.service.RSIranking.batch.measurement.JobExecutionTimeListener;
import com.service.RSIranking.batch.measurement.StepExecutionTimeListener;
import com.service.RSIranking.dto.StockDto;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.List;

@Configuration
public class DailyTradingInformationUpdateBatch {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager platformTransactionManager;
    private final RedisTemplate<String, List<StockDto>> redisTemplate;
    private final JobExecutionTimeListener jobExecutionTimeListener;
    private final StepExecutionTimeListener stepExecutionTimeListener;


    public DailyTradingInformationUpdateBatch(JobRepository jobRepository,
                                 @Qualifier("metaTransactionManager") PlatformTransactionManager platformTransactionManager,
                                 @Qualifier("stockRedisTemplate")RedisTemplate<String, List<StockDto>> redisTemplate,
                                 JobExecutionTimeListener jobExecutionTimeListener,
                                 StepExecutionTimeListener stepExecutionTimeListener)
    {
        this.jobRepository =  jobRepository;
        this.platformTransactionManager = platformTransactionManager;
        this.redisTemplate = redisTemplate;
        this.jobExecutionTimeListener = jobExecutionTimeListener;
        this.stepExecutionTimeListener = stepExecutionTimeListener;
    }

    // todo 일별 매매 정도 업데이트 job
//    @Bean
//    public Job DailyTradingInformationUpdateJob() {
//        return new JobBuilder("stockUpdateJob", jobRepository)
//                .listener(jobExecutionTimeListener)
//                .start()
//                .end()
//                .build();
//    }
    // todo 일별 매매 정보 가져오는 step
    
    // todo 일별 매맴 정보 DB에 저장 step
}
