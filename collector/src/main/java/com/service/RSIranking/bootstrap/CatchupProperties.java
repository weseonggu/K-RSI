package com.service.RSIranking.bootstrap;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 기동 시 캐치업(catch-up) 수집 설정 프로퍼티 클래스.
 *
 * <p>application.yml의 bootstrap.catchup 프로퍼티를 바인딩합니다.</p>
 *
 * <h2>설정 항목</h2>
 * <ul>
 *   <li>enabled: 기동 시 캐치업 실행 여부 (기본 false)</li>
 *   <li>backfillDays: DB가 비어 있을 때 종료일로부터 되짚어 수집할 달력일 수 (기본 200)</li>
 *   <li>lagDays: KRX 데이터 지연 보정 일수 — 수집 종료일 = 오늘(KST) - lagDays (기본 1)</li>
 *   <li>intervalMs: 일자 간 KRX 호출 간격 밀리초 (기본 200)</li>
 *   <li>drainSecs: RSI 스트림 리스너가 결과를 적재할 때까지 대기하는 시간 초 (기본 15)</li>
 * </ul>
 *
 * @author RSIranking Team
 * @version 1.0
 * @see CatchupBootstrap
 */
@Component
@ConfigurationProperties(prefix = "bootstrap.catchup")
@Getter
@Setter
public class CatchupProperties {
    /** 기동 시 캐치업 실행 여부 */
    private boolean enabled = false;
    /** DB가 비어 있을 때 종료일로부터 되짚어 수집할 달력일 수 */
    private int backfillDays = 200;
    /** KRX 데이터 지연 보정 일수 (수집 종료일 = 오늘 - lagDays) */
    private int lagDays = 1;
    /** 일자 간 KRX 호출 간격(ms) — KRX API 쿼터 보호용 throttle */
    private long intervalMs = 200;
    /** RSI 스트림 drain 대기 시간(초) */
    private long drainSecs = 15;
}
