package com.service.RSIranking.batch.stock_info_job;

import com.service.RSIranking.batch.measurement.JobExecutionTimeListener;
import com.service.RSIranking.batch.measurement.StepExecutionTimeListener;
import com.service.RSIranking.batch.stock_info_job.step.CompareAndUpdateProcessor;
import com.service.RSIranking.batch.stock_info_job.step.GetStockInfoToDBReader;
import com.service.RSIranking.batch.stock_info_job.step.GetStockInfoToKRXTasklet;
import com.service.RSIranking.batch.stock_info_job.step.UpdateStockInfoWriter;
import com.service.RSIranking.entity.StockInfoEntity;
import com.service.RSIranking.repository.jpa.SecuritiesStockRepository;
import com.service.RSIranking.service.InterStepDataSharingWithRedisService;
import com.service.RSIranking.service.KrxRequestService;
import com.service.RSIranking.service.StockBulkInsertService;
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

@Configuration
public class SecuritiesStocksBatch {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager platformTransactionManager;
    private final SecuritiesStockRepository securitiesStockRepository;
    private final JobExecutionTimeListener jobExecutionTimeListener;
    private final StepExecutionTimeListener stepExecutionTimeListener;
    private final KrxRequestService krxRequestService;
    private final InterStepDataSharingWithRedisService interStepDataSharingWithRedisService;
    private final StockBulkInsertService stockBulkInsertService;

    //-------------------------------Step들-----------------------------------------------
    private final GetStockInfoToKRXTasklet getStockInfoToKRXTasklet;
    private final GetStockInfoToDBReader getStockInfoToDBReader;
    private final CompareAndUpdateProcessor compareAndUpdateProcessor;
    private final UpdateStockInfoWriter updateStockInfoWriter;

    public SecuritiesStocksBatch(JobRepository jobRepository,
                                 @Qualifier("metaTransactionManager") PlatformTransactionManager platformTransactionManager,
                                 SecuritiesStockRepository securitiesStockRepository,
                                 JobExecutionTimeListener jobExecutionTimeListener,
                                 StepExecutionTimeListener stepExecutionTimeListener,
                                 KrxRequestService krxRequestService,
                                 InterStepDataSharingWithRedisService interStepDataSharingWithRedisService,
                                 StockBulkInsertService stockBulkInsertService,
                                 GetStockInfoToKRXTasklet getStockInfoToKRXTasklet,
                                 GetStockInfoToDBReader getStockInfoToDBReader,
                                 CompareAndUpdateProcessor compareAndUpdateProcessor,
                                 UpdateStockInfoWriter updateStockInfoWriter)
    {
    this.jobRepository =  jobRepository;
    this.platformTransactionManager = platformTransactionManager;
    this.securitiesStockRepository = securitiesStockRepository;
    this.jobExecutionTimeListener = jobExecutionTimeListener;
    this.stepExecutionTimeListener = stepExecutionTimeListener;
    this.krxRequestService = krxRequestService;
    this.interStepDataSharingWithRedisService = interStepDataSharingWithRedisService;
    this.stockBulkInsertService = stockBulkInsertService;

    this.getStockInfoToKRXTasklet = getStockInfoToKRXTasklet;
    this.getStockInfoToDBReader = getStockInfoToDBReader;
    this.compareAndUpdateProcessor = compareAndUpdateProcessor;
    this.updateStockInfoWriter = updateStockInfoWriter;
    }

// ====================================JoB=================================================
    // 증권 종목 업데이트 Job
    @Bean
    public Job SecuritiesStocksUpdateJob() {
        return new JobBuilder("stockUpdateJob", jobRepository)
                .listener(jobExecutionTimeListener)// 배치 실행시간 측정 리스너
                .start(requestKRXAPIStep())// KRX API 데이터 요청
                .on("NO_DATA").end() // 데이터가 없으면 잡 종료
                .on("REDIS_FAILED").end()// 레디스 저장 실패 시 잡 종료
                .from(requestKRXAPIStep())
                .on("*").to(updateDatabaseStep()) // 데이터가 있으면 다음 스텝 실행
                .end()
                .build();
    }
// ===============================STEP1===============================================
    // KRX에 데이터 요청 step
    @Bean
    public Step requestKRXAPIStep() {
        return new StepBuilder("requestKRXAPIStep", jobRepository)
                .tasklet(getStockInfoToKRXTasklet , platformTransactionManager)
                .listener(getStockInfoToKRXTasklet)
                .listener(fetchDataListener() )
                .listener(stepExecutionTimeListener)// 스텝 실행시간 기록 리스너
                .build();
    }
//    @Bean
//    @StepScope
//    public GetStockInfoToKRXTasklet fetchDataTasklet() {
//        return new GetStockInfoToKRXTasklet(krxRequestService, interStepDataSharingWithRedisService);
//    }
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
                .<StockInfoEntity, StockInfoEntity>chunk(10, platformTransactionManager)
                .reader(getStockInfoToDBReader)
                .processor(compareAndUpdateProcessor) // 종목 데이터 비교 실행
                .writer(updateStockInfoWriter)
                .listener(compareAndUpdateProcessor) // 리스너로 등록해야 beforeStep 실행됨
                .listener(getStockInfoToDBReader) // DB 읽기전 청크 사이즈에 따라 페이지 사이즈 설정하고 시장 설정
                .listener(stepExecutionTimeListener)// 스텝 실행시간 기록 리스너
                .build();
    }
    // DB 데이터 읽어 오기
//    @Bean(name = "stockEntityItemReaderForSecurities")
//    @StepScope
//    public GetStockInfoToDBReader stockEntityItemReader(Integer pageSize){
//        return new GetStockInfoToDBReader(securitiesStockRepository, pageSize);
//    }
    // DB 데이터랑 api요청으로 가져온 데이터 비교하기
//    @Bean
//    @StepScope
//    public CompareAndUpdateProcessor compareAndUpdateProcessor(){
//        return new CompareAndUpdateProcessor(interStepDataSharingWithRedisService,stockBulkInsertService, securitiesStockRepository);
//    }
    // proccess 결과 DB에 저장하기
//    @Bean
//    public ItemWriter<StockInfoEntity> newStockWriter() {
//        return new UpdateStockInfoWriter(securitiesStockRepository);
//    }

}
