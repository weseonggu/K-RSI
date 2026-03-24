package com.service.RSIranking.integration.batch;

import com.service.RSIranking.integration.AbstractIntegrationTest;
import com.service.RSIranking.schedule.AsyncJobLauncher;
import com.service.RSIranking.util.MarketDayForTheLast14Days;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.*;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 마스터 Job 실행 통합 테스트.
 *
 * <p>AsyncJobLauncher를 Mock 처리하여 외부 API 호출 없이
 * 마스터 Job의 순차 실행 로직을 검증합니다.</p>
 *
 * <h2>테스트 시나리오</h2>
 * <ul>
 *   <li>성공: 3개 Step이 순차적으로 모두 완료되는지</li>
 *   <li>실패 전파: Step 1 실패 시 Step 2, 3이 실행되지 않는지</li>
 *   <li>중간 실패: Step 2 실패 시 Step 3이 실행되지 않는지</li>
 * </ul>
 *
 * @author RSIranking Team
 * @version 1.0
 */
class MasterJobExecutionTest extends AbstractIntegrationTest {

    @MockitoBean
    private AsyncJobLauncher asyncJobLauncher;

    @MockitoBean
    private MarketDayForTheLast14Days marketDayForTheLast14Days;

    @Autowired
    private JobLauncher jobLauncher;

    @Autowired
    @Qualifier("masterPipelineJob")
    private Job masterPipelineJob;

    @Autowired
    private JobRepository jobRepository;

    private int executionCount = 0;

    @BeforeEach
    void setUp() {
        executionCount++;

        // MarketDayForTheLast14Days Mock - RSI 계산 Step에서 사용
        List<LocalDate> fakeDays = List.of(
                LocalDate.of(2025, 3, 14),
                LocalDate.of(2025, 3, 13),
                LocalDate.of(2025, 3, 12),
                LocalDate.of(2025, 3, 11),
                LocalDate.of(2025, 3, 10),
                LocalDate.of(2025, 3, 7),
                LocalDate.of(2025, 3, 6),
                LocalDate.of(2025, 3, 5),
                LocalDate.of(2025, 3, 4),
                LocalDate.of(2025, 2, 28),
                LocalDate.of(2025, 2, 27),
                LocalDate.of(2025, 2, 26),
                LocalDate.of(2025, 2, 25),
                LocalDate.of(2025, 2, 24)
        );
        when(marketDayForTheLast14Days.getMarketDayForTheLast14Days(any()))
                .thenReturn(fakeDays);
    }

    /**
     * 마스터 Job 실행용 고유한 JobParameters를 생성합니다.
     * 매 실행마다 다른 executionDate를 사용하여 중복 방지.
     */
    private JobParameters createJobParameters() {
        return new JobParametersBuilder()
                .addString("executionDate", "test-" + System.currentTimeMillis() + "-" + executionCount)
                .addString("yesterday", "20250314")
                .toJobParameters();
    }

    // ==================== 성공 시나리오 ====================

    @Test
    @DisplayName("모든 Step 성공 시 마스터 Job이 COMPLETED 상태로 종료되어야 한다")
    void masterJob_AllStepsSuccess_ShouldComplete() throws Exception {
        // given - 모든 하위 Job이 성공하도록 Mock 설정
        when(asyncJobLauncher.runKospiInfoJob(any()))
                .thenReturn(CompletableFuture.completedFuture(null));
        when(asyncJobLauncher.runKosdaqInfoJob(any()))
                .thenReturn(CompletableFuture.completedFuture(null));
        when(asyncJobLauncher.runKospiTradingJob(any()))
                .thenReturn(CompletableFuture.completedFuture(null));
        when(asyncJobLauncher.runKosdaqTradingJob(any()))
                .thenReturn(CompletableFuture.completedFuture(null));
        when(asyncJobLauncher.runKospiRSICalculationJob(any()))
                .thenReturn(CompletableFuture.completedFuture(null));
        when(asyncJobLauncher.runKosdaqRSICalculationJob(any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        // when - 마스터 Job 실행
        JobExecution jobExecution = jobLauncher.run(masterPipelineJob, createJobParameters());

        // then - COMPLETED 상태 확인
        assertThat(jobExecution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(jobExecution.getExitStatus().getExitCode()).isEqualTo("COMPLETED");
    }

    @Test
    @DisplayName("모든 Step 성공 시 3개 Step이 모두 실행되어야 한다")
    void masterJob_AllStepsSuccess_ShouldExecuteAllThreeSteps() throws Exception {
        // given
        when(asyncJobLauncher.runKospiInfoJob(any()))
                .thenReturn(CompletableFuture.completedFuture(null));
        when(asyncJobLauncher.runKosdaqInfoJob(any()))
                .thenReturn(CompletableFuture.completedFuture(null));
        when(asyncJobLauncher.runKospiTradingJob(any()))
                .thenReturn(CompletableFuture.completedFuture(null));
        when(asyncJobLauncher.runKosdaqTradingJob(any()))
                .thenReturn(CompletableFuture.completedFuture(null));
        when(asyncJobLauncher.runKospiRSICalculationJob(any()))
                .thenReturn(CompletableFuture.completedFuture(null));
        when(asyncJobLauncher.runKosdaqRSICalculationJob(any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        // when
        JobExecution jobExecution = jobLauncher.run(masterPipelineJob, createJobParameters());

        // then - 3개 Step 모두 실행 확인
        Collection<StepExecution> stepExecutions = jobExecution.getStepExecutions();
        assertThat(stepExecutions).hasSize(3);

        List<String> stepNames = stepExecutions.stream()
                .map(StepExecution::getStepName)
                .toList();
        assertThat(stepNames).containsExactly(
                "stockUpdateStep",
                "tradingInfoUpdateStep",
                "rsiCalculationStep"
        );

        // 모든 Step이 COMPLETED
        stepExecutions.forEach(step ->
                assertThat(step.getStatus()).isEqualTo(BatchStatus.COMPLETED)
        );
    }

    @Test
    @DisplayName("성공 시 6개 하위 Job이 모두 호출되어야 한다 (KOSPI/KOSDAQ 각 3쌍)")
    void masterJob_AllStepsSuccess_ShouldInvokeAllSubJobs() throws Exception {
        // given
        when(asyncJobLauncher.runKospiInfoJob(any()))
                .thenReturn(CompletableFuture.completedFuture(null));
        when(asyncJobLauncher.runKosdaqInfoJob(any()))
                .thenReturn(CompletableFuture.completedFuture(null));
        when(asyncJobLauncher.runKospiTradingJob(any()))
                .thenReturn(CompletableFuture.completedFuture(null));
        when(asyncJobLauncher.runKosdaqTradingJob(any()))
                .thenReturn(CompletableFuture.completedFuture(null));
        when(asyncJobLauncher.runKospiRSICalculationJob(any()))
                .thenReturn(CompletableFuture.completedFuture(null));
        when(asyncJobLauncher.runKosdaqRSICalculationJob(any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        // when
        jobLauncher.run(masterPipelineJob, createJobParameters());

        // then - 6개 하위 Job 호출 확인
        verify(asyncJobLauncher).runKospiInfoJob(any());
        verify(asyncJobLauncher).runKosdaqInfoJob(any());
        verify(asyncJobLauncher).runKospiTradingJob(any());
        verify(asyncJobLauncher).runKosdaqTradingJob(any());
        verify(asyncJobLauncher).runKospiRSICalculationJob(any());
        verify(asyncJobLauncher).runKosdaqRSICalculationJob(any());
    }

    // ==================== 실패 시나리오 ====================

    @Test
    @DisplayName("Step 1(종목 정보) 실패 시 마스터 Job이 FAILED 상태로 종료되어야 한다")
    void masterJob_Step1Fails_ShouldFail() throws Exception {
        // given - Step 1 KOSPI가 실패
        when(asyncJobLauncher.runKospiInfoJob(any()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("KOSPI 종목 정보 수집 실패")));
        when(asyncJobLauncher.runKosdaqInfoJob(any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        // when
        JobExecution jobExecution = jobLauncher.run(masterPipelineJob, createJobParameters());

        // then - FAILED 상태
        assertThat(jobExecution.getStatus()).isEqualTo(BatchStatus.FAILED);
    }

    @Test
    @DisplayName("Step 1 실패 시 Step 2, 3은 실행되지 않아야 한다")
    void masterJob_Step1Fails_ShouldNotExecuteStep2And3() throws Exception {
        // given - Step 1 실패
        when(asyncJobLauncher.runKospiInfoJob(any()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("실패")));
        when(asyncJobLauncher.runKosdaqInfoJob(any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        // when
        JobExecution jobExecution = jobLauncher.run(masterPipelineJob, createJobParameters());

        // then - Step 1만 실행됨
        Collection<StepExecution> stepExecutions = jobExecution.getStepExecutions();
        assertThat(stepExecutions).hasSize(1);
        assertThat(stepExecutions.iterator().next().getStepName()).isEqualTo("stockUpdateStep");

        // Step 2, 3 관련 메서드는 호출되지 않음
        verify(asyncJobLauncher, never()).runKospiTradingJob(any());
        verify(asyncJobLauncher, never()).runKosdaqTradingJob(any());
        verify(asyncJobLauncher, never()).runKospiRSICalculationJob(any());
        verify(asyncJobLauncher, never()).runKosdaqRSICalculationJob(any());
    }

    @Test
    @DisplayName("Step 2(매매 정보) 실패 시 Step 3은 실행되지 않아야 한다")
    void masterJob_Step2Fails_ShouldNotExecuteStep3() throws Exception {
        // given - Step 1 성공, Step 2 KOSDAQ 실패
        when(asyncJobLauncher.runKospiInfoJob(any()))
                .thenReturn(CompletableFuture.completedFuture(null));
        when(asyncJobLauncher.runKosdaqInfoJob(any()))
                .thenReturn(CompletableFuture.completedFuture(null));
        when(asyncJobLauncher.runKospiTradingJob(any()))
                .thenReturn(CompletableFuture.completedFuture(null));
        when(asyncJobLauncher.runKosdaqTradingJob(any()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("KOSDAQ 매매 정보 수집 실패")));

        // when
        JobExecution jobExecution = jobLauncher.run(masterPipelineJob, createJobParameters());

        // then - FAILED 상태, Step 1과 2만 실행됨
        assertThat(jobExecution.getStatus()).isEqualTo(BatchStatus.FAILED);

        Collection<StepExecution> stepExecutions = jobExecution.getStepExecutions();
        assertThat(stepExecutions).hasSize(2);

        List<String> stepNames = stepExecutions.stream()
                .map(StepExecution::getStepName)
                .toList();
        assertThat(stepNames).containsExactly("stockUpdateStep", "tradingInfoUpdateStep");

        // Step 3 관련 메서드는 호출되지 않음
        verify(asyncJobLauncher, never()).runKospiRSICalculationJob(any());
        verify(asyncJobLauncher, never()).runKosdaqRSICalculationJob(any());
    }

    @Test
    @DisplayName("Step 3(RSI 계산) 실패 시 마스터 Job이 FAILED 상태로 종료되어야 한다")
    void masterJob_Step3Fails_ShouldFail() throws Exception {
        // given - Step 1, 2 성공, Step 3 실패
        when(asyncJobLauncher.runKospiInfoJob(any()))
                .thenReturn(CompletableFuture.completedFuture(null));
        when(asyncJobLauncher.runKosdaqInfoJob(any()))
                .thenReturn(CompletableFuture.completedFuture(null));
        when(asyncJobLauncher.runKospiTradingJob(any()))
                .thenReturn(CompletableFuture.completedFuture(null));
        when(asyncJobLauncher.runKosdaqTradingJob(any()))
                .thenReturn(CompletableFuture.completedFuture(null));
        when(asyncJobLauncher.runKospiRSICalculationJob(any()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("RSI 계산 실패")));
        when(asyncJobLauncher.runKosdaqRSICalculationJob(any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        // when
        JobExecution jobExecution = jobLauncher.run(masterPipelineJob, createJobParameters());

        // then - 3개 Step 모두 실행되었으나 마스터 Job은 FAILED
        assertThat(jobExecution.getStatus()).isEqualTo(BatchStatus.FAILED);

        Collection<StepExecution> stepExecutions = jobExecution.getStepExecutions();
        assertThat(stepExecutions).hasSize(3);
    }
}
