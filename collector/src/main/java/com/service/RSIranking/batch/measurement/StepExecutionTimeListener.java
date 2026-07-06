package com.service.RSIranking.batch.measurement;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Step 실행 시간 측정 리스너.
 *
 * <p>배치 Step의 시작과 종료 시점을 감지하여 실행 시간을 로깅합니다.</p>
 *
 * <p>Spring Batch가 자동으로 기록하는 {@link StepExecution#getStartTime()}/
 * {@link StepExecution#getEndTime()}을 사용해 duration을 계산합니다.
 * 인스턴스 필드를 사용하지 않으므로 동시 실행되는 Step들 사이에 race condition이 없습니다.</p>
 *
 * @author RSIranking Team
 * @version 1.1
 */
@Component
@Slf4j
public class StepExecutionTimeListener implements StepExecutionListener {

    /**
     * Step 실행 전 시작 정보를 로깅합니다.
     *
     * @param stepExecution Step 실행 정보
     */
    @Override
    public void beforeStep(StepExecution stepExecution) {
        log.info("Step 시작: {} [thread={}]",
                stepExecution.getStepName(),
                Thread.currentThread().getName());
    }

    /**
     * Step 완료 후 실행 시간을 로깅합니다.
     *
     * <p>{@link StepExecution#getStartTime()}과 {@link StepExecution#getEndTime()}을
     * 사용해 duration을 계산합니다 (인스턴스 필드 race 없음).</p>
     *
     * @param stepExecution Step 실행 정보
     * @return Step 종료 상태
     */
    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        LocalDateTime start = stepExecution.getStartTime();
        LocalDateTime end = stepExecution.getEndTime();
        long durationMs = (start != null && end != null)
                ? Duration.between(start, end).toMillis()
                : -1L;
        log.info("Step 종료: {} (총 실행 시간: {} ms) [thread={}]",
                stepExecution.getStepName(),
                durationMs,
                Thread.currentThread().getName());
        return StepExecutionListener.super.afterStep(stepExecution);
    }
}
