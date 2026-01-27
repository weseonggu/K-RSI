package com.service.RSIranking.config.DB;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jdbc.repository.config.EnableJdbcRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import javax.sql.DataSource;

/**
 * JDBC 데이터 DB 설정 클래스.
 *
 * <p>대량 데이터 처리를 위한 JDBC Template 설정을 제공합니다.
 * JPA의 N+1 문제를 회피하고 벌크 연산 성능을 최적화하기 위해 사용됩니다.</p>
 *
 * <h2>제공 빈</h2>
 * <ul>
 *   <li>JdbcTemplate: 기본 JDBC 작업용</li>
 *   <li>NamedParameterJdbcTemplate: 명명된 파라미터 지원</li>
 * </ul>
 *
 * @author RSIranking Team
 * @version 1.0
 */
@Configuration
@Profile({"dev", "prod", "test"})
@EnableJdbcRepositories(
        basePackages = "com.yourpackage.repository",
        jdbcOperationsRef = "jdbcDataJdbcOperations"
)
public class JDBCDataDBConfig {
// data source 하나로 변경으로인한 미사용 주석 처리
//    @Bean(name = "jdbcDataSource")
//    @ConfigurationProperties(prefix = "spring.datasource-data")
//    public DataSource dataSource() {
//        return DataSourceBuilder.create().build();
//    }

    /**
     * JDBC Template을 생성합니다.
     *
     * @param dataSource 데이터 DataSource
     * @return JdbcTemplate 빈
     */
    @Bean(name = "jdbcDataTemplate")
    public JdbcTemplate jdbcTemplate(@Qualifier("dataDBSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }


    /**
     * 명명된 파라미터를 지원하는 JDBC Template을 생성합니다.
     *
     * @param jdbcTemplate 기본 JdbcTemplate
     * @return NamedParameterJdbcTemplate 빈
     */
    @Bean
    public NamedParameterJdbcTemplate namedParameterJdbcTemplate(
            @Qualifier("jdbcDataTemplate") JdbcTemplate jdbcTemplate) {
        return new NamedParameterJdbcTemplate(jdbcTemplate);
    }
}


