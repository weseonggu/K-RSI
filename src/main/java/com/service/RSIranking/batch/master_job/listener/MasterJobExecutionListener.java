package com.service.RSIranking.batch.master_job.listener;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.stereotype.Component;

/**
 * 마스터 Job 실행 리스너.
 *
 * <p>마스터 파이프라인 Job의 시작과 종료를 감지하여 로깅합니다.</p>
 *
 * <p>로깅 내용:</p>
 * <ul>
 *   <li>Job 시작 시: Job 이름, 대상 날짜</li>
 *   <li>Job 종료 시: 최종 상태, 총 실행 시간, 실패 원인 (있는 경우)</li>
 * </ul>
 *
 * @author RSIranking Team
 * @version 1.0
 */
@Component
@Slf4j
public class MasterJobExecutionListener implements JobExecutionListener {

    private long startTime;

    /**
     * 마스터 Job 시작 전 호출됩니다.
     *
     * @param jobExecution Job 실행 정보
     */
    @Override
    public void beforeJob(JobExecution jobExecution) {
        startTime = System.currentTimeMillis();
        log.info("========================================");
        log.info("마스터 파이프라인 Job 시작: {}", jobExecution.getJobInstance().getJobName());
        log.info("대상 날짜: {}", jobExecution.getJobParameters().getString("yesterday"));
        log.info("실행 시간: {}", jobExecution.getJobParameters().getString("executionDate"));
        log.info("========================================");
    }

    /**
     * 마스터 Job 종료 후 호출됩니다.
     *
     * @param jobExecution Job 실행 정보
     */
    @Override
    public void afterJob(JobExecution jobExecution) {
        long duration = System.currentTimeMillis() - startTime;
        BatchStatus status = jobExecution.getStatus();

        log.info("========================================");
        log.info("마스터 파이프라인 Job 종료");
        log.info("최종 상태: {}", status);
        log.info("총 실행 시간: {} ms ({} 분 {} 초)",
                duration,
                duration / 60000,
                (duration % 60000) / 1000);

        if (status == BatchStatus.FAILED) {
            log.error("실패 원인:");
            jobExecution.getAllFailureExceptions().forEach(ex ->
                log.error("  - {}: {}", ex.getClass().getSimpleName(), ex.getMessage())
            );
        }

        log.info("========================================");
    }
}
