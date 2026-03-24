package com.service.RSIranking.integration.batch;

import com.service.RSIranking.integration.AbstractIntegrationTest;
import com.service.RSIranking.schedule.MasterJobLauncher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 마스터 Job 통합 테스트.
 *
 * <p>Testcontainers를 사용하여 MySQL과 Redis 환경에서
 * 마스터 Job 구성을 검증합니다.</p>
 *
 * @author RSIranking Team
 * @version 1.0
 */
class MasterJobIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    @Qualifier("masterPipelineJob")
    private Job masterPipelineJob;

    @Autowired
    @Qualifier("stockUpdateStep")
    private Step stockUpdateStep;

    @Autowired
    @Qualifier("tradingInfoUpdateStep")
    private Step tradingInfoUpdateStep;

    @Autowired
    @Qualifier("rsiCalculationStep")
    private Step rsiCalculationStep;

    @Test
    @DisplayName("마스터 파이프라인 Job Bean이 올바르게 생성되어야 한다")
    void masterPipelineJob_ShouldBeCreated() {
        assertThat(masterPipelineJob).isNotNull();
        assertThat(masterPipelineJob.getName()).isEqualTo("masterPipelineJob");
    }

    @Test
    @DisplayName("stockUpdateStep Bean이 올바르게 생성되어야 한다")
    void stockUpdateStep_ShouldBeCreated() {
        assertThat(stockUpdateStep).isNotNull();
        assertThat(stockUpdateStep.getName()).isEqualTo("stockUpdateStep");
    }

    @Test
    @DisplayName("tradingInfoUpdateStep Bean이 올바르게 생성되어야 한다")
    void tradingInfoUpdateStep_ShouldBeCreated() {
        assertThat(tradingInfoUpdateStep).isNotNull();
        assertThat(tradingInfoUpdateStep.getName()).isEqualTo("tradingInfoUpdateStep");
    }

    @Test
    @DisplayName("rsiCalculationStep Bean이 올바르게 생성되어야 한다")
    void rsiCalculationStep_ShouldBeCreated() {
        assertThat(rsiCalculationStep).isNotNull();
        assertThat(rsiCalculationStep.getName()).isEqualTo("rsiCalculationStep");
    }

    @Test
    @DisplayName("MasterJobLauncher Bean이 조건에 따라 생성되지 않아야 한다 (enabled=false)")
    void masterJobLauncher_ShouldNotBeCreatedWhenDisabled() {
        // scheduler.master.enabled=false이므로 Bean이 생성되지 않아야 함
        assertThat(applicationContext.containsBean("masterJobLauncher")).isFalse();
    }

    @Test
    @DisplayName("Testcontainers MySQL 연결이 정상이어야 한다")
    void testcontainersMySQL_ShouldBeConnected() {
        assertThat(MYSQL_DATA.isRunning()).isTrue();
        assertThat(MYSQL_META.isRunning()).isTrue();
    }

    @Test
    @DisplayName("Testcontainers Redis 연결이 정상이어야 한다")
    void testcontainersRedis_ShouldBeConnected() {
        assertThat(REDIS.isRunning()).isTrue();
    }
}
