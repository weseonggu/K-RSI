package com.service.RSIranking.config.DB;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import javax.sql.DataSource;

/**
 * 데이터 DB 공용 DataSource 설정 클래스.
 *
 * <p>비즈니스 데이터 DB에 접속하는 단일 DataSource를 정의합니다. 이 DataSource는
 * JPA({@link JPADataDBConfig})와 JDBC({@link JDBCDataDBConfig})가 함께 사용합니다.
 * 이전에는 JPA 설정 클래스 안에 포함되어 있었으나, 두 접근 방식이 공유하는 자원이므로
 * 별도 설정 클래스로 분리했습니다.</p>
 *
 * <h2>설정 항목</h2>
 * <ul>
 *   <li>Bean: {@code dataDBSource}</li>
 *   <li>프로퍼티 접두사: {@code spring.datasource-data}</li>
 * </ul>
 *
 * <h2>커넥션 풀 설정</h2>
 * <p>HikariCP 풀 크기·타임아웃 등 세부 설정을 코드에 하드코딩하지 않고
 * {@code spring.datasource-data.*} 프로퍼티(application-*.yml)로 바인딩합니다.
 * 따라서 재배포 없이 yml(또는 환경변수)로 조정할 수 있습니다. 바인딩 가능한 주요 키:</p>
 * <ul>
 *   <li>{@code maximum-pool-size} — 최대 풀 사이즈</li>
 *   <li>{@code minimum-idle} — 최소 idle 커넥션</li>
 *   <li>{@code connection-timeout} — 커넥션 획득 타임아웃(ms)</li>
 *   <li>{@code leak-detection-threshold} — 커넥션 누수 감지 임계값(ms)</li>
 *   <li>{@code pool-name} — 풀 이름</li>
 * </ul>
 *
 * @author RSIranking Team
 * @version 1.0
 */
@Configuration
@Profile({"dev", "prod", "test"})
public class DataDBSourceConfig {

    /**
     * 비즈니스 데이터 DB용 공용 DataSource를 생성합니다.
     *
     * <p>커넥션 정보와 HikariCP 풀 설정은 모두 {@code spring.datasource-data} 프로퍼티로
     * 바인딩됩니다. KOSPI/KOSDAQ 자식 Job들이 동시에 chunk commit을 회전시키므로 풀이
     * 좁으면 사실상 직렬화됩니다 — 풀 크기는 yml에서 워크로드에 맞게 조정합니다.</p>
     *
     * @return 데이터 DataSource
     */
    @Bean(name = "dataDBSource")
    @ConfigurationProperties(prefix = "spring.datasource-data")
    public DataSource dataDBSource() {
        return DataSourceBuilder.create().type(HikariDataSource.class).build();
    }
}
