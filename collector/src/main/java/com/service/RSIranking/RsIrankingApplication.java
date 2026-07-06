package com.service.RSIranking;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * RSI 지표 계산 배치 애플리케이션의 메인 클래스.
 *
 * <p>한국거래소(KRX) API를 통해 KOSPI/KOSDAQ 종목 정보 및 일별 매매 정보를 수집하고,
 * RSI(Relative Strength Index, 상대강도지수) 지표를 계산하는 Spring Batch 기반 애플리케이션입니다.</p>
 *
 * <h2>주요 기능</h2>
 * <ul>
 *   <li>KRX API를 통한 종목 정보 수집 및 업데이트</li>
 *   <li>일별 매매 정보(종가, 거래량 등) 수집</li>
 *   <li>RSI 지표 계산 (Wilder's Smoothing Method 적용)</li>
 *   <li>Redis Stream을 활용한 비동기 메시지 처리</li>
 * </ul>
 *
 * <h2>활성화된 기능</h2>
 * <ul>
 *   <li>{@code @EnableScheduling} - 스케줄링 기반 배치 작업 실행</li>
 *   <li>{@code @EnableRetry} - API 호출 실패 시 재시도 메커니즘</li>
 * </ul>
 *
 * @author RSIranking Team
 * @version 1.0
 * @since 2024
 */
@SpringBootApplication
@EnableScheduling
@EnableRetry
public class RsIrankingApplication {

	/**
	 * 애플리케이션의 진입점.
	 *
	 * @param args 커맨드 라인 인자
	 */
	public static void main(String[] args) {
		SpringApplication.run(RsIrankingApplication.class, args);
	}

}
