package com.service.RSIranking.batch.measurement;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Job 실행 시간 측정 리스너.
 *
 * <p>배치 Job의 시작과 종료 시점을 감지하여 총 실행 시간을 로깅합니다.</p>
 *
 * <p>Spring Batch가 자동으로 기록하는 {@link JobExecution#getStartTime()}/
 * {@link JobExecution#getEndTime()}을 사용해 duration을 계산합니다.
 * 인스턴스 필드를 사용하지 않으므로 동시 실행되는 Job들 사이에 race condition이 없습니다.</p>
 *
 * @author RSIranking Team
 * @version 1.1
 */
@Component
@Slf4j
public class JobExecutionTimeListener implements JobExecutionListener {

    /**
     * Job 실행 전 시작 시간을 로깅합니다.
     *
     * <p>실제 시작 시각은 Spring Batch가 {@link JobExecution#getStartTime()}에 기록합니다.</p>
     *
     * @param jobExecution Job 실행 정보
     */
    @Override
    public void beforeJob(JobExecution jobExecution) {
        log.info("배치 시작: {} [thread={}]",
                jobExecution.getJobInstance().getJobName(),
                Thread.currentThread().getName());
    }

    /**
     * Job 완료 후 총 실행 시간을 로깅합니다.
     *
     * <p>{@link JobExecution#getStartTime()}과 {@link JobExecution#getEndTime()}을
     * 사용해 duration을 계산합니다 (인스턴스 필드 race 없음).</p>
     *
     * @param jobExecution Job 실행 정보
     */
    @Override
    public void afterJob(JobExecution jobExecution) {
        LocalDateTime start = jobExecution.getStartTime();
        LocalDateTime end = jobExecution.getEndTime();
        long durationMs = (start != null && end != null)
                ? Duration.between(start, end).toMillis()
                : -1L;
        log.info("배치 종료: {} (총 실행 시간: {} ms) [thread={}]",
                jobExecution.getJobInstance().getJobName(),
                durationMs,
                Thread.currentThread().getName());
    }
}
