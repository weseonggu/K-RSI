package com.service.RSIranking.batch.rsi_calculation_job;


import com.service.RSIranking.batch.measurement.JobExecutionTimeListener;
import com.service.RSIranking.batch.measurement.StepExecutionTimeListener;
import com.service.RSIranking.batch.rsi_calculation_job.step.MessageProduceWriter;
import com.service.RSIranking.batch.rsi_calculation_job.step.RSIMessageMakeProccess;
import com.service.RSIranking.batch.stock_info_job.step.GetStockInfoToDBReader;
import com.service.RSIranking.dto.RSIMessageDTO;

import com.service.RSIranking.entity.inter.StockInfoEntity;
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
 * RSI 계산 배치 Job 설정 클래스.
 *
 * <p>데이터베이스에서 종목 정보를 읽어 RSI 계산 메시지를 생성하고,
 * Redis Stream에 발행하는 배치 작업을 구성합니다.</p>
 *
 * <h2>Job 구성</h2>
 * <pre>
 * RSICalculationJob
 *   └── produceRSIMessageStep
 *         ├── Reader: GetStockInfoToDBReader (DB에서 종목 정보 읽기)
 *         ├── Processor: RSIMessageMakeProccess (RSI 메시지 DTO 생성)
 *         └── Writer: MessageProduceWriter (Redis Stream에 메시지 발행)
 * </pre>
 *
 * <h2>청크 처리</h2>
 * <p>50개 단위로 청크 처리하여 메모리 효율성을 확보합니다.</p>
 *
 * @author RSIranking Team
 * @version 1.0
 * @see RSIMessageMakeProccess
 * @see MessageProduceWriter
 */
@Configuration
public class RSICalculationBatch {
    private final JobRepository jobRepository;
    private final PlatformTransactionManager platformTransactionManager;
    private final JobExecutionTimeListener jobExecutionTimeListener;
    private final StepExecutionTimeListener stepExecutionTimeListener;

    //----------------------------Step들--------------------------------------------
    private final GetStockInfoToDBReader getStockInfoToDBReader;
    private final RSIMessageMakeProccess rsiMessageMakeProccess;
    private final MessageProduceWriter messageProduceWriter;
    public RSICalculationBatch(JobRepository jobRepository,
                                 @Qualifier("metaTransactionManager") PlatformTransactionManager platformTransactionManager,
                                 JobExecutionTimeListener jobExecutionTimeListener,
                                 StepExecutionTimeListener stepExecutionTimeListener,
                               GetStockInfoToDBReader getStockInfoToDBReader,
                               RSIMessageMakeProccess rsiMessageMakeProccess,
                               MessageProduceWriter messageProduceWriter)
    {
        this.jobRepository =  jobRepository;
        this.platformTransactionManager = platformTransactionManager;
        this.jobExecutionTimeListener = jobExecutionTimeListener;
        this.stepExecutionTimeListener = stepExecutionTimeListener;
        this.getStockInfoToDBReader = getStockInfoToDBReader;

        this.rsiMessageMakeProccess = rsiMessageMakeProccess;
        this.messageProduceWriter = messageProduceWriter;


    }
    // =======================================JOB=========================================

    /**
     * RSI 계산 메시지 생성 Job을 정의합니다.
     *
     * <p>종목 정보를 읽어 RSI 계산에 필요한 메시지를 생성하고
     * Redis Stream에 발행하는 Job입니다.</p>
     *
     * @return RSI 계산 Job
     */
    @Bean
    public Job RSICalculationJob(){
        return new JobBuilder("RSICalculationJob", jobRepository)
                .listener(jobExecutionTimeListener)
                .start(produceRSIMessageStep())
                .build();
    }

    // =======================================Step========================================
    /**
     * RSI 메시지 생성 Step을 정의합니다.
     *
     * <p>DB에서 종목 정보를 읽어 RSI 계산 메시지를 생성하고
     * Redis Stream에 발행합니다.</p>
     *
     * @return RSI 메시지 생성 Step
     */
    @Bean
    public Step produceRSIMessageStep() {
        return new StepBuilder("produceRSIMessageStep", jobRepository)
                .<StockInfoEntity, RSIMessageDTO>chunk(50, platformTransactionManager)
                .reader(getStockInfoToDBReader)// DB에서 종목 데이터 읽어 오기
                .processor(rsiMessageMakeProccess) // 메세지 생성
                .writer(messageProduceWriter) // 메세지 큐에 전송
                .listener(getStockInfoToDBReader)
                .listener(stepExecutionTimeListener) // step 실행시간 측정 리스너
                .build();
    }
}
