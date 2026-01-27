package com.service.RSIranking.batch.stock_info_job;

import com.service.RSIranking.batch.measurement.JobExecutionTimeListener;
import com.service.RSIranking.batch.measurement.StepExecutionTimeListener;
import com.service.RSIranking.batch.stock_info_job.step.CompareAndUpdateProcessor;
import com.service.RSIranking.batch.stock_info_job.step.GetStockInfoToDBReader;
import com.service.RSIranking.batch.stock_info_job.step.GetStockInfoToKRXTasklet;
import com.service.RSIranking.batch.stock_info_job.step.UpdateStockInfoWriter;

import com.service.RSIranking.entity.inter.StockInfoEntity;
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
 * 증권 종목 정보 업데이트 배치 Job 설정 클래스.
 *
 * <p>KRX API를 통해 최신 종목 정보를 조회하고, 데이터베이스의 기존 정보와 비교하여
 * 변경사항을 업데이트하는 배치 작업을 구성합니다.</p>
 *
 * <h2>Job 구성</h2>
 * <pre>
 * SecuritiesStocksUpdateJob
 *   ├── Step 1: requestKRXAPIStep (KRX API 데이터 요청)
 *   │     └── Tasklet: GetStockInfoToKRXTasklet
 *   └── Step 2: updateDatabaseStep (DB 업데이트)
 *         ├── Reader: GetStockInfoToDBReader
 *         ├── Processor: CompareAndUpdateProcessor
 *         └── Writer: UpdateStockInfoWriter
 * </pre>
 *
 * <h2>종료 조건</h2>
 * <ul>
 *   <li>NO_DATA: API 응답에 데이터가 없는 경우</li>
 *   <li>REDIS_FAILED: Redis 저장 실패 시</li>
 * </ul>
 *
 * @author RSIranking Team
 * @version 1.0
 */
@Configuration
public class SecuritiesStocksBatch {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager platformTransactionManager;
    private final JobExecutionTimeListener jobExecutionTimeListener;
    private final StepExecutionTimeListener stepExecutionTimeListener;

    private final GetStockInfoToKRXTasklet getStockInfoToKRXTasklet;
    private final GetStockInfoToDBReader getStockInfoToDBReader;
    private final CompareAndUpdateProcessor compareAndUpdateProcessor;
    private final UpdateStockInfoWriter updateStockInfoWriter;

    public SecuritiesStocksBatch(JobRepository jobRepository,
                                 @Qualifier("metaTransactionManager") PlatformTransactionManager platformTransactionManager,
                                 JobExecutionTimeListener jobExecutionTimeListener,
                                 StepExecutionTimeListener stepExecutionTimeListener,
                                 GetStockInfoToKRXTasklet getStockInfoToKRXTasklet,
                                 GetStockInfoToDBReader getStockInfoToDBReader,
                                 CompareAndUpdateProcessor compareAndUpdateProcessor,
                                 UpdateStockInfoWriter updateStockInfoWriter)
    {
    this.jobRepository =  jobRepository;
    this.platformTransactionManager = platformTransactionManager;
    this.jobExecutionTimeListener = jobExecutionTimeListener;
    this.stepExecutionTimeListener = stepExecutionTimeListener;

    this.getStockInfoToKRXTasklet = getStockInfoToKRXTasklet;
    this.getStockInfoToDBReader = getStockInfoToDBReader;
    this.compareAndUpdateProcessor = compareAndUpdateProcessor;
    this.updateStockInfoWriter = updateStockInfoWriter;
    }

// ====================================JoB=================================================
    /**
     * 증권 종목 업데이트 Job을 정의합니다.
     *
     * <p>KRX API에서 종목 데이터를 요청하고, 기존 DB 데이터와 비교하여
     * 신규 상장, 상장폐지, 종목명 변경 등을 처리합니다.</p>
     *
     * @return 종목 업데이트 Job
     */
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
    /**
     * KRX API 데이터 요청 Step을 정의합니다.
     *
     * <p>KRX Open API를 호출하여 종목 정보를 가져오고
     * Redis에 임시 저장합니다.</p>
     *
     * @return KRX API 요청 Step
     */
    @Bean
    public Step requestKRXAPIStep() {
        return new StepBuilder("requestKRXAPIStep", jobRepository)
                .tasklet(getStockInfoToKRXTasklet , platformTransactionManager)
                .listener(getStockInfoToKRXTasklet)
                .listener(fetchDataListener() )
                .listener(stepExecutionTimeListener)// 스텝 실행시간 기록 리스너
                .build();
    }
    @Bean
    public ExecutionContextPromotionListener fetchDataListener() {
        ExecutionContextPromotionListener listener = new ExecutionContextPromotionListener();
        listener.setKeys(new String[] {"StockDtoList"});
        return listener;
    }

// ==============================STEP2=====================================================
    /**
     * 데이터베이스 업데이트 Step을 정의합니다.
     *
     * <p>Redis에 저장된 KRX 데이터와 DB의 기존 데이터를 비교하여
     * 변경사항을 업데이트합니다.</p>
     *
     * @return DB 업데이트 Step
     */
    @Bean
    public Step updateDatabaseStep() {
        return new StepBuilder("updateDatabaseStep", jobRepository)
                .<StockInfoEntity, StockInfoEntity>chunk(50, platformTransactionManager)
                .reader(getStockInfoToDBReader)
                .processor(compareAndUpdateProcessor) // 종목 데이터 비교 실행
                .writer(updateStockInfoWriter)
                .listener(compareAndUpdateProcessor) // 리스너로 등록해야 beforeStep 실행됨
                .listener(getStockInfoToDBReader) // DB 읽기전 청크 사이즈에 따라 페이지 사이즈 설정하고 시장 설정
                .listener(stepExecutionTimeListener)// 스텝 실행시간 기록 리스너
                .build();
    }
}
