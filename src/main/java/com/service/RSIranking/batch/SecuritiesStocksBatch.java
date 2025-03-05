package com.service.RSIranking.batch;

import com.service.RSIranking.entity.AfterEntity;
import com.service.RSIranking.entity.BeforeEntity;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
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

    public SecuritiesStocksBatch(JobRepository jobRepository,
                                 @Qualifier("metaTransactionManager") PlatformTransactionManager platformTransactionManager)
    {
    this.jobRepository =  jobRepository;
    this.platformTransactionManager = platformTransactionManager;
    }

//    @Bean
//    public Job SecuritiesStocksUpdateJob(){
//        return new JobBuilder("firstJob", jobRepository)
//                .start()
//                .build();
//    }

//    @Bean
//    public Step firstStep() {
//
//        System.out.println("first step");
//
//        return new StepBuilder("firstStep", jobRepository)
//                .<BeforeEntity, AfterEntity> chunk(10, platformTransactionManager)
////                .reader(beforeReader())
////                .processor(middleProcessor())
////                .writer(afterWriter())
//                .build();
//    }
}
