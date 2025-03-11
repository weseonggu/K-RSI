package com.service.RSIranking.batch;

import com.service.RSIranking.batch.step.CompareAndUpdateProcessor;
import com.service.RSIranking.batch.step.DBStockReader;
import com.service.RSIranking.batch.step.FetchDataTasklet;
import com.service.RSIranking.batch.step.StockWriter;
import com.service.RSIranking.dto.SecuritiesStockDto;
import com.service.RSIranking.entity.SecuritiesStockEntity;
import com.service.RSIranking.repository.SecuritiesStockRepository;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.listener.ExecutionContextPromotionListener;
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

import java.util.List;

@Configuration
public class SecuritiesStocksBatch {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager platformTransactionManager;
    private final SecuritiesStockRepository securitiesStockRepository;
    private final RedisTemplate<String, List<SecuritiesStockDto>> redisTemplate;

    private String mktNm;

    public SecuritiesStocksBatch(JobRepository jobRepository,
                                 @Qualifier("metaTransactionManager") PlatformTransactionManager platformTransactionManager,
                                 SecuritiesStockRepository securitiesStockRepository,
                                 @Qualifier("stockRedisTemplate")RedisTemplate<String, List<SecuritiesStockDto>> redisTemplate)
    {
    this.jobRepository =  jobRepository;
    this.platformTransactionManager = platformTransactionManager;
    this.securitiesStockRepository = securitiesStockRepository;
    this.redisTemplate = redisTemplate;
    }

// ====================================JoB=================================================
    // 유가 증권 종목 업데이트 Job
    // 주 1회 금요일에 실행 하도록
    @Bean
    public Job SecuritiesStocksUpdateJob(){
        return new JobBuilder("stockUpdateJob", jobRepository)
                .start(requestKRXAPIStep())
                .next(updateDatabaseStep())
                .build();
    }
// ===============================STEP1===============================================
    // KRX에 데이터 요청 step
    @Bean
    public Step requestKRXAPIStep() {
        return new StepBuilder("requestKRXAPIStep", jobRepository)
                .tasklet(fetchDataTasklet() , platformTransactionManager)
                .listener(fetchDataTasklet())
                .listener(fetchDataListener() )
                .build();
    }
    @Bean
    public FetchDataTasklet fetchDataTasklet() {
        return new FetchDataTasklet(redisTemplate);
    }
    @Bean
    public ExecutionContextPromotionListener fetchDataListener() {
        ExecutionContextPromotionListener listener = new ExecutionContextPromotionListener();
        listener.setKeys(new String[] {"StockDtoList"});
        return listener;
    }

// ==============================STEP2=====================================================
    // DB에 있는 데이터 업데이트 step
    // todo chunk 크기 yml 파일 에서 관리하도록 변경 필요
    @Bean
    public Step updateDatabaseStep() {
        return new StepBuilder("updateDatabaseStep", jobRepository)
                .<SecuritiesStockEntity, SecuritiesStockEntity>chunk(10, platformTransactionManager)
                .reader(stockEntityItemReader())
                .processor(compareAndUpdateProcessor()) // 기존 processor 추가
                .writer(newStockWriter())
                .listener(compareAndUpdateProcessor()) // 리스너로 등록해야 @BeforeStep 실행됨
                .listener(stockEntityItemReader())
                .build();
    }
    // DB 데이터 읽어 오기
    @Bean
    public ItemReader<SecuritiesStockEntity> stockEntityItemReader(){
        return new DBStockReader(securitiesStockRepository);
    }
    // DB 데이터랑 api요청으로 가져온 데이터 비교하기
    @Bean
    public ItemProcessor<SecuritiesStockEntity, SecuritiesStockEntity> compareAndUpdateProcessor(){
        return new CompareAndUpdateProcessor(securitiesStockRepository, redisTemplate);
    }
    // proccess 결과 DB에 저장하기
    @Bean
    public ItemWriter<SecuritiesStockEntity> newStockWriter() {
        return new StockWriter(securitiesStockRepository);
    }


}
