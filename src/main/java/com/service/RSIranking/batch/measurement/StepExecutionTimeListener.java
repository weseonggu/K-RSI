package com.service.RSIranking.batch.measurement;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.stereotype.Component;

/**
 * Step 실행 시간 측정 리스너.
 *
 * <p>배치 Step의 시작과 종료 시점을 감지하여 실행 시간을 로깅합니다.</p>
 *
 * @author RSIranking Team
 * @version 1.0
 */
@Component
@Slf4j
public class StepExecutionTimeListener implements StepExecutionListener{

    private long startTime;

    /**
     * Step 실행 전 시작 시간을 기록합니다.
     *
     * @param stepExecution Step 실행 정보
     */
    @Override
    public void beforeStep(StepExecution stepExecution) {
        startTime = System.currentTimeMillis();
        log.info("Step 시작: {}", stepExecution.getStepName());
    }

    /**
     * Step 완료 후 실행 시간을 로깅합니다.
     *
     * @param stepExecution Step 실행 정보
     * @return Step 종료 상태
     */
    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        long duration = System.currentTimeMillis() - startTime;
        log.info("Step 종료: {} (총 실행 시간: {} ms)", stepExecution.getStepName(), duration);
        return StepExecutionListener.super.afterStep(stepExecution);
    }

}
