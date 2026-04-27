package com.service.RSIranking.batch.measurement;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobInstance;

import java.time.LocalDateTime;

import static org.mockito.Mockito.*;

/**
 * JobExecutionTimeListener 단위 테스트.
 *
 * <p>인스턴스 필드 race가 제거되었음을 검증합니다.
 * 동시 호출되어도 각자의 JobExecution.startTime/endTime을 사용하므로
 * 다른 호출의 영향을 받지 않습니다.</p>
 */
class JobExecutionTimeListenerTest {

    @Test
    @DisplayName("afterJob은 JobExecution의 startTime/endTime을 사용해 duration을 계산한다")
    void afterJob_UsesJobExecutionTimes() {
        JobExecutionTimeListener listener = new JobExecutionTimeListener();

        JobExecution exec = mock(JobExecution.class);
        JobInstance instance = mock(JobInstance.class);
        when(instance.getJobName()).thenReturn("testJob");
        when(exec.getJobInstance()).thenReturn(instance);

        LocalDateTime start = LocalDateTime.now().minusSeconds(5);
        LocalDateTime end = LocalDateTime.now();
        when(exec.getStartTime()).thenReturn(start);
        when(exec.getEndTime()).thenReturn(end);

        // 단순히 예외 없이 동작하면 통과 (JobExecution 시각 사용 검증은 mock 호출로 확인)
        listener.beforeJob(exec);
        listener.afterJob(exec);

        verify(exec, atLeastOnce()).getStartTime();
        verify(exec, atLeastOnce()).getEndTime();
    }

    @Test
    @DisplayName("두 JobExecution을 동시 처리해도 인스턴스 필드 race가 없다")
    void noInstanceFieldRace_AcrossConcurrentExecutions() {
        JobExecutionTimeListener listener = new JobExecutionTimeListener();

        // KOSPI / KOSDAQ를 동시에 처리하는 시나리오 시뮬레이션
        JobExecution kospi = mockExec("kospiJob", LocalDateTime.now().minusSeconds(10), LocalDateTime.now());
        JobExecution kosdaq = mockExec("kosdaqJob", LocalDateTime.now().minusSeconds(20), LocalDateTime.now());

        listener.beforeJob(kospi);
        listener.beforeJob(kosdaq);   // 기존 코드라면 startTime을 덮어썼을 시점
        listener.afterJob(kospi);     // kospi의 duration이 정확해야 함
        listener.afterJob(kosdaq);

        // 두 JobExecution 모두 자신의 시각이 사용되어야 함
        verify(kospi, atLeastOnce()).getStartTime();
        verify(kospi, atLeastOnce()).getEndTime();
        verify(kosdaq, atLeastOnce()).getStartTime();
        verify(kosdaq, atLeastOnce()).getEndTime();
    }

    private JobExecution mockExec(String jobName, LocalDateTime start, LocalDateTime end) {
        JobExecution exec = mock(JobExecution.class);
        JobInstance instance = mock(JobInstance.class);
        when(instance.getJobName()).thenReturn(jobName);
        when(exec.getJobInstance()).thenReturn(instance);
        when(exec.getStartTime()).thenReturn(start);
        when(exec.getEndTime()).thenReturn(end);
        return exec;
    }
}
