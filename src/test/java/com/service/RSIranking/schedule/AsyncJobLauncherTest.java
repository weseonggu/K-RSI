package com.service.RSIranking.schedule;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.configuration.JobRegistry;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.launch.NoSuchJobException;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * AsyncJobLauncher 단위 테스트.
 *
 * <p>JobExecution.getStatus() 검사 로직 (Phase 3-3) 을 검증합니다.
 * COMPLETED가 아닌 status로 끝난 자식 Job은 failedFuture로 반환되어야 한다.</p>
 */
class AsyncJobLauncherTest {

    private JobLauncher jobLauncher;
    private JobRegistry jobRegistry;
    private AsyncJobLauncher asyncJobLauncher;

    @BeforeEach
    void setUp() throws NoSuchJobException {
        jobLauncher = mock(JobLauncher.class);
        jobRegistry = mock(JobRegistry.class);
        Job stubJob = mock(Job.class);
        when(jobRegistry.getJob(any())).thenReturn(stubJob);
        asyncJobLauncher = new AsyncJobLauncher(jobLauncher, jobRegistry);
    }

    @Test
    @DisplayName("자식 Job이 COMPLETED 상태이면 completedFuture를 반환한다")
    void runKospiInfoJob_ReturnsCompleted_WhenJobStatusIsCompleted() throws Exception {
        // given
        JobExecution execution = mock(JobExecution.class);
        when(execution.getStatus()).thenReturn(BatchStatus.COMPLETED);
        when(jobLauncher.run(any(), any())).thenReturn(execution);

        // when
        CompletableFuture<Void> future = asyncJobLauncher.runKospiInfoJob(new JobParameters());

        // then
        assertThat(future).isCompleted();
        assertThat(future.isCompletedExceptionally()).isFalse();
    }

    @Test
    @DisplayName("자식 Job이 FAILED 상태이면 failedFuture를 반환한다")
    void runKospiInfoJob_ReturnsFailed_WhenJobStatusIsFailed() throws Exception {
        // given
        JobExecution execution = mock(JobExecution.class);
        when(execution.getStatus()).thenReturn(BatchStatus.FAILED);
        when(jobLauncher.run(any(), any())).thenReturn(execution);

        // when
        CompletableFuture<Void> future = asyncJobLauncher.runKospiInfoJob(new JobParameters());

        // then
        assertThat(future).isCompletedExceptionally();
        assertThatThrownBy(future::get)
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("자식 Job이 STOPPED 상태이면 failedFuture를 반환한다")
    void runKospiTradingJob_ReturnsFailed_WhenJobStatusIsStopped() throws Exception {
        // given
        JobExecution execution = mock(JobExecution.class);
        when(execution.getStatus()).thenReturn(BatchStatus.STOPPED);
        when(jobLauncher.run(any(), any())).thenReturn(execution);

        // when
        CompletableFuture<Void> future = asyncJobLauncher.runKospiTradingJob(new JobParameters());

        // then
        assertThat(future).isCompletedExceptionally();
    }

    @Test
    @DisplayName("JobLauncher가 예외를 던지면 failedFuture를 반환한다")
    void runKospiInfoJob_ReturnsFailed_WhenJobLauncherThrows() throws Exception {
        // given
        when(jobLauncher.run(any(), any())).thenThrow(new RuntimeException("boom"));

        // when
        CompletableFuture<Void> future = asyncJobLauncher.runKospiInfoJob(new JobParameters());

        // then
        assertThat(future).isCompletedExceptionally();
        assertThatThrownBy(future::get)
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(RuntimeException.class)
                .hasMessageContaining("boom");
    }

    @Test
    @DisplayName("KOSDAQ Info / RSI / Trading 메서드도 동일한 status 검사를 수행한다")
    void allKosdaqMethods_PerformStatusCheck() throws Exception {
        // given
        JobExecution failed = mock(JobExecution.class);
        when(failed.getStatus()).thenReturn(BatchStatus.FAILED);
        when(jobLauncher.run(any(), any())).thenReturn(failed);

        // when / then
        assertThat(asyncJobLauncher.runKosdaqInfoJob(new JobParameters())).isCompletedExceptionally();
        assertThat(asyncJobLauncher.runKosdaqTradingJob(new JobParameters())).isCompletedExceptionally();
        assertThat(asyncJobLauncher.runKosdaqRSICalculationJob(new JobParameters())).isCompletedExceptionally();
        assertThat(asyncJobLauncher.runKospiRSICalculationJob(new JobParameters())).isCompletedExceptionally();
    }
}
