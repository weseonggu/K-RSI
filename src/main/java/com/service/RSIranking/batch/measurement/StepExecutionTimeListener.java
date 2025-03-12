package com.service.RSIranking.batch.measurement;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class StepExecutionTimeListener implements StepExecutionListener{

    private long startTime;

    @Override
    public void beforeStep(StepExecution stepExecution) {
        startTime = System.currentTimeMillis();
        log.info("Step 시작: {}", stepExecution.getStepName());
    }

    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        long duration = System.currentTimeMillis() - startTime;
        log.info("Step 종료: {} (총 실행 시간: {} ms)", stepExecution.getStepName(), duration);
        return StepExecutionListener.super.afterStep(stepExecution);
    }

}
