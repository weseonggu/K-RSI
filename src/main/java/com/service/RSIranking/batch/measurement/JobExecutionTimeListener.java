package com.service.RSIranking.batch.measurement;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.stereotype.Component;

/**
 * Job 실행 시간 측정 리스너.
 *
 * <p>배치 Job의 시작과 종료 시점을 감지하여 총 실행 시간을 로깅합니다.</p>
 *
 * @author RSIranking Team
 * @version 1.0
 */
@Component
@Slf4j
public class JobExecutionTimeListener implements JobExecutionListener {

    private long startTime;

    /**
     * Job 실행 전 시작 시간을 기록합니다.
     *
     * @param jobExecution Job 실행 정보
     */
    @Override
    public void beforeJob(JobExecution jobExecution) {
        startTime = System.currentTimeMillis();
        log.info("배치 시작: {}", jobExecution.getJobInstance().getJobName());
    }

    /**
     * Job 완료 후 총 실행 시간을 로깅합니다.
     *
     * @param jobExecution Job 실행 정보
     */
    @Override
    public void afterJob(JobExecution jobExecution) {
        long duration = System.currentTimeMillis() - startTime;
        log.info("배치 종료: {} (총 실행 시간: {} ms)", jobExecution.getJobInstance().getJobName(), duration);
    }
}

