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

    @Bean(name = "jdbcDataTemplate")
    public JdbcTemplate jdbcTemplate(@Qualifier("dataDBSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }


    @Bean
    public NamedParameterJdbcTemplate namedParameterJdbcTemplate(
            @Qualifier("jdbcDataTemplate") JdbcTemplate jdbcTemplate) {
        return new NamedParameterJdbcTemplate(jdbcTemplate);
    }
}


