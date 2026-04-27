package com.service.RSIranking.config.DB;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.util.HashMap;

/**
 * JPA 데이터 DB 설정 클래스.
 *
 * <p>종목 정보, 매매 정보 등 비즈니스 데이터를 저장하는
 * 데이터베이스의 JPA 연결을 설정합니다.</p>
 *
 * <h2>설정 항목</h2>
 * <ul>
 *   <li>DataSource: spring.datasource-data 프로퍼티 사용</li>
 *   <li>EntityManager: dataEntityManager</li>
 *   <li>TransactionManager: dataTransactionManager</li>
 *   <li>대상 Repository: com.service.RSIranking.repository.jpa</li>
 * </ul>
 *
 * <h2>Hibernate 설정</h2>
 * <ul>
 *   <li>DDL 자동 생성: update</li>
 *   <li>SQL 로깅: 활성화</li>
 * </ul>
 *
 * @author RSIranking Team
 * @version 1.0
 */
@Configuration
@Profile({"dev", "prod", "test"})
@EnableJpaRepositories(
        basePackages = "com.service.RSIranking.repository.jpa",
        entityManagerFactoryRef = "dataEntityManager",
        transactionManagerRef = "dataTransactionManager"
)
public class JPADataDBConfig {

    /**
     * 비즈니스 데이터 DB용 DataSource를 생성합니다.
     *
     * <p>HikariCP 풀 사이즈를 명시적으로 설정합니다. KOSPI/KOSDAQ 자식 Job들이 동시에
     * chunk(50) commit을 회전시키므로 풀이 좁으면 사실상 직렬화됩니다.
     * 최소 8, 안전 20으로 설정합니다.</p>
     *
     * @return 데이터 DataSource
     */
    @Bean(name = "dataDBSource")
    @ConfigurationProperties(prefix = "spring.datasource-data")
    public DataSource dataDBSource() {
        HikariDataSource ds = DataSourceBuilder.create().type(HikariDataSource.class).build();
        ds.setMaximumPoolSize(20);
        ds.setMinimumIdle(5);
        ds.setConnectionTimeout(30_000L);
        ds.setLeakDetectionThreshold(60_000L);
        ds.setPoolName("DataDBPool");
        return ds;
    }
    /**
     * JPA EntityManagerFactory를 생성합니다.
     *
     * <p>Hibernate를 JPA 구현체로 사용하며, 엔티티 패키지를 스캔합니다.</p>
     *
     * @param dataSource 데이터 DataSource
     * @return EntityManagerFactory 빈
     */
    @Bean(name = "dataEntityManager")
    public LocalContainerEntityManagerFactoryBean dataEntityManager(
            @Qualifier("dataDBSource") DataSource dataSource) {

        LocalContainerEntityManagerFactoryBean em = new LocalContainerEntityManagerFactoryBean();

        em.setDataSource(dataSource);
        em.setPackagesToScan("com.service.RSIranking.entity"); // jpa 엔티티 패키지
        em.setJpaVendorAdapter(new HibernateJpaVendorAdapter());

        HashMap<String, Object> properties = new HashMap<>();
        properties.put("hibernate.hbm2ddl.auto", "update");
        properties.put("hibernate.show_sql", "true");
        properties.put("hibernate.format_sql", "true");
        em.setJpaPropertyMap(properties);

        return em;
    }
    /**
     * JPA 트랜잭션 매니저를 생성합니다.
     *
     * <p>{@code @Transactional("dataTransactionManager")} 어노테이션으로 사용합니다.</p>
     *
     * @param entityManagerFactory EntityManagerFactory 빈
     * @return JPA 트랜잭션 매니저
     */
    @Bean(name = "dataTransactionManager")
    public PlatformTransactionManager dataTransactionManager(
            @Qualifier("dataEntityManager") LocalContainerEntityManagerFactoryBean entityManagerFactory) {

        return new JpaTransactionManager(entityManagerFactory.getObject());
    }
}
