package com.service.RSIranking.config.DB;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

/**
 * Spring Batch 메타데이터 DB 설정 클래스.
 *
 * <p>Spring Batch의 Job 실행 이력, Step 실행 이력 등 메타데이터를 저장하는
 * 데이터베이스 연결을 설정합니다.</p>
 *
 * <h2>설정 항목</h2>
 * <ul>
 *   <li>DataSource: spring.datasource-meta 프로퍼티 사용</li>
 *   <li>TransactionManager: metaTransactionManager (Primary)</li>
 *   <li>HikariCP 풀: maximum-pool-size=10, minimum-idle=3</li>
 * </ul>
 *
 * @author RSIranking Team
 * @version 1.1
 */
@Configuration
@Profile({"dev", "prod", "test"})
public class MetaDBConfig {

    /**
     * 메타데이터 DB용 DataSource를 생성합니다.
     *
     * <p>HikariCP 풀 사이즈를 명시적으로 설정합니다. 마스터 잡과 자식 KOSPI/KOSDAQ Job들이
     * 동시에 JobRepository write를 수행하므로 SERIALIZABLE 격리 락 큐를 위해 최소 6, 안전 10을 사용합니다.</p>
     *
     * @return 메타데이터 DataSource
     */
    @Primary
    @Bean(name = "metaDBSource")
    @ConfigurationProperties(prefix = "spring.datasource-meta")
    public DataSource metaDBSource() {
        HikariDataSource ds = DataSourceBuilder.create().type(HikariDataSource.class).build();
        ds.setMaximumPoolSize(10);
        ds.setMinimumIdle(3);
        ds.setConnectionTimeout(30_000L);
        ds.setLeakDetectionThreshold(60_000L);
        ds.setPoolName("MetaDBPool");
        return ds;
    }

    /**
     * 메타데이터 DB용 트랜잭션 매니저를 생성합니다.
     *
     * @param dataSource 메타데이터 DataSource
     * @return 트랜잭션 매니저
     */
    @Primary
    @Bean(name = "metaTransactionManager")
    public PlatformTransactionManager metaTransactionManager(
            @Qualifier("metaDBSource") DataSource dataSource) {

        return new DataSourceTransactionManager(dataSource);
    }
}
