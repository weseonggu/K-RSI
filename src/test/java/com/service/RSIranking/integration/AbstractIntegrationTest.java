package com.service.RSIranking.integration;

import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * 통합 테스트 베이스 클래스.
 *
 * <p>모든 통합 테스트는 이 클래스를 상속받아 Testcontainers를 사용합니다.
 * Singleton 컨테이너 패턴으로 테스트 성능을 최적화합니다.</p>
 *
 * <h2>제공되는 컨테이너</h2>
 * <ul>
 *   <li>MySQL (RSIData): 비즈니스 데이터용</li>
 *   <li>MySQL (RSIMeta): Spring Batch 메타데이터용</li>
 *   <li>Redis: 캐시 및 메시지 스트림용</li>
 * </ul>
 *
 * <h2>사용 방법</h2>
 * <pre>
 * class MyIntegrationTest extends AbstractIntegrationTest {
 *     &#64;Test
 *     void testSomething() {
 *         // 테스트 코드
 *     }
 * }
 * </pre>
 *
 * @author RSIranking Team
 * @version 1.0
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class AbstractIntegrationTest {

    // Singleton Containers - 모든 테스트에서 재사용 (protected for subclass access)
    protected static final MySQLContainer<?> MYSQL_DATA;
    protected static final MySQLContainer<?> MYSQL_META;
    protected static final GenericContainer<?> REDIS;

    static {
        // MySQL for RSIData (비즈니스 데이터)
        MYSQL_DATA = new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
                .withDatabaseName("RSIData")
                .withUsername("test")
                .withPassword("test")
                .withReuse(true)
                .withCommand(
                        "--character-set-server=utf8mb4",
                        "--collation-server=utf8mb4_unicode_ci",
                        "--max_connections=200"
                );

        // MySQL for RSIMeta (Spring Batch 메타데이터)
        MYSQL_META = new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
                .withDatabaseName("RSIMeta")
                .withUsername("test")
                .withPassword("test")
                .withReuse(true);

        // Redis Container
        REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.2-alpine"))
                .withExposedPorts(6379)
                .withReuse(true)
                .withCommand("redis-server", "--requirepass", "testpass");

        // 컨테이너 병렬 시작
        MYSQL_DATA.start();
        MYSQL_META.start();
        REDIS.start();
    }

    /**
     * Testcontainers의 동적 프로퍼티를 Spring 환경에 주입합니다.
     *
     * @param registry 프로퍼티 레지스트리
     */
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // Data DB (비즈니스 데이터)
        registry.add("spring.datasource-data.jdbc-url", () ->
                MYSQL_DATA.getJdbcUrl() + "?rewriteBatchedStatements=true");
        registry.add("spring.datasource-data.username", MYSQL_DATA::getUsername);
        registry.add("spring.datasource-data.password", MYSQL_DATA::getPassword);
        registry.add("spring.datasource-data.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");

        // Meta DB (Spring Batch 메타데이터)
        registry.add("spring.datasource-meta.jdbc-url", MYSQL_META::getJdbcUrl);
        registry.add("spring.datasource-meta.username", MYSQL_META::getUsername);
        registry.add("spring.datasource-meta.password", MYSQL_META::getPassword);
        registry.add("spring.datasource-meta.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");

        // Redis
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.data.redis.password", () -> "testpass");

        // Batch 설정
        registry.add("spring.batch.jdbc.initialize-schema", () -> "always");

        // 모든 스케줄러 비활성화
        registry.add("scheduler.stockinfo.enabled", () -> "false");
        registry.add("scheduler.dailytranding.enabled", () -> "false");
        registry.add("scheduler.rsiproducer.enabled", () -> "false");
        registry.add("scheduler.rsiconsumer.enabled", () -> "false");
        registry.add("scheduler.rsistreamlistener.enabled", () -> "false");
        registry.add("scheduler.master.enabled", () -> "false");
    }
}
