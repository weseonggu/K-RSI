package com.service.RSIranking.batch.step;

import com.service.RSIranking.config.krx_api.ApiConfig;
import com.service.RSIranking.service.InterStepDataSharingWithRedisService;
import com.service.RSIranking.service.KrxRequestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.annotation.BeforeStep;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.repeat.RepeatStatus;

@RequiredArgsConstructor
@Slf4j
public class RequestDailyTradingInfoTasklet implements Tasklet {

    private StepExecution stepExecution;
    private ApiConfig apiConfig =  new ApiConfig();
    private String mktNM;
    private String date;

    private final KrxRequestService krxRequestService;
    private final InterStepDataSharingWithRedisService interStepDataSharingWithRedisService;


    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {

        JobExecution jobExecution = contribution.getStepExecution().getJobExecution();
        ExecutionContext jobContext = jobExecution.getExecutionContext();

        return RepeatStatus.FINISHED;
    }

    @BeforeStep
    public void saveStepExecution(StepExecution stepExecution) {

        this.stepExecution = stepExecution;

        JobParameters jobParameters = stepExecution.getJobParameters();
        this.apiConfig.setUrl(jobParameters.getString("apiUrl"));
        this.apiConfig.setKey(jobParameters.getString("apiKey"));
        this.mktNM = jobParameters.getString("mktNm");
        this.date = jobParameters.getString("yesterday");
        log.info(this.apiConfig.getUrl()+" "+this.date+" "+this.mktNM);


    }
}
