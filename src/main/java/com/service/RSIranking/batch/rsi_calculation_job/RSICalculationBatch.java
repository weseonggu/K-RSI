package com.service.RSIranking.batch.rsi_calculation_job;


import com.service.RSIranking.batch.measurement.JobExecutionTimeListener;
import com.service.RSIranking.batch.measurement.StepExecutionTimeListener;
import com.service.RSIranking.batch.rsi_calculation_job.step.MessageProduceWriter;
import com.service.RSIranking.batch.rsi_calculation_job.step.RSIMessageMakeProccess;
import com.service.RSIranking.batch.stock_info_job.step.GetStockInfoToDBReader;
import com.service.RSIranking.dto.RSIMessageDTO;
import com.service.RSIranking.entity.SecuritiesStockEntity;
import com.service.RSIranking.repository.jpa.SecuritiesStockRepository;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class RSICalculationBatch {
    private final JobRepository jobRepository;
    private final PlatformTransactionManager platformTransactionManager;
    private final SecuritiesStockRepository securitiesStockRepository;
    private final JobExecutionTimeListener jobExecutionTimeListener;
    private final StepExecutionTimeListener stepExecutionTimeListener;
    private final RedisTemplate<String, Object> redisTemplate;

    public RSICalculationBatch(JobRepository jobRepository,
                                 @Qualifier("metaTransactionManager") PlatformTransactionManager platformTransactionManager,
                                 @Qualifier("rsiMessageRedisTemplate") RedisTemplate redisTemplate,
                                 SecuritiesStockRepository securitiesStockRepository,
                                 JobExecutionTimeListener jobExecutionTimeListener,
                                 StepExecutionTimeListener stepExecutionTimeListener)
    {
        this.jobRepository =  jobRepository;
        this.platformTransactionManager = platformTransactionManager;
        this.securitiesStockRepository = securitiesStockRepository;
        this.jobExecutionTimeListener = jobExecutionTimeListener;
        this.stepExecutionTimeListener = stepExecutionTimeListener;
        this.redisTemplate = redisTemplate;

    }
    // =======================================JOB=========================================

    @Bean
    public Job RSICalculationJob(){
        return new JobBuilder("RSICalculationJob", jobRepository)
                .listener(jobExecutionTimeListener)
                .start(produceRSIMessageStep())
                .build();
    }

    // =======================================Step========================================
    @Bean
    public Step produceRSIMessageStep() {
        return new StepBuilder("produceRSIMessageStep", jobRepository)
                .<SecuritiesStockEntity, RSIMessageDTO>chunk(10, platformTransactionManager)
                .reader(stockEntityItemReader(10))
                .processor(makeMessage())
                .writer(produceMessage())
                .listener(stockEntityItemReader(10))
                .listener(stepExecutionTimeListener)
                .build();
    }
    // DB 데이터 읽어 오기
    @Bean(name = "stockEntityItemReaderForRSI")
    public ItemReader<SecuritiesStockEntity> stockEntityItemReader(int pageSize){
        return new GetStockInfoToDBReader(securitiesStockRepository, pageSize);
    }
    // 메세지 만들기 프로세스
    @Bean
    public ItemProcessor<SecuritiesStockEntity, RSIMessageDTO> makeMessage(){
        return new RSIMessageMakeProccess();
    }
    // 메제시 전송
    @Bean
    public ItemWriter<RSIMessageDTO> produceMessage(){
        return new MessageProduceWriter(redisTemplate);
    }

}
