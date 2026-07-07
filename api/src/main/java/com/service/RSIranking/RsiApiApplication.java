package com.service.RSIranking;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * RSI 순위 조회 REST API 애플리케이션의 메인 클래스.
 *
 * <p>수집기(collector)가 적재한 일별 RSI 지표를 조회하는 REST API를 제공합니다.
 * 데이터 DB와 Redis는 수집기와 공유하며, 영속성 계층(엔티티/리포지토리/DB 설정)은
 * common 모듈에서 가져옵니다.</p>
 *
 * <h2>제공 API</h2>
 * <ul>
 *   <li>{@code GET /api/rsi/ranking} - 일별 RSI 순위 조회</li>
 * </ul>
 *
 * @author RSIranking Team
 * @version 1.0
 */
@SpringBootApplication
public class RsiApiApplication {

    /**
     * 애플리케이션의 진입점.
     *
     * @param args 커맨드 라인 인자
     */
    public static void main(String[] args) {
        SpringApplication.run(RsiApiApplication.class, args);
    }
}
