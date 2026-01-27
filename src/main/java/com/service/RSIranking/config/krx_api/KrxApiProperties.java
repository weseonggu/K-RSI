package com.service.RSIranking.config.krx_api;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * KRX API 설정 프로퍼티 클래스.
 *
 * <p>application.yml의 api.krx 프로퍼티를 바인딩하여 KRX API 설정을 관리합니다.</p>
 *
 * <h2>설정 항목</h2>
 * <ul>
 *   <li>key: API 인증 키</li>
 *   <li>kospiInfoUrl: KOSPI 종목 정보 API URL</li>
 *   <li>kosdaqInfoUrl: KOSDAQ 종목 정보 API URL</li>
 *   <li>kospiTradingInfoUrl: KOSPI 매매 정보 API URL</li>
 *   <li>kosdaqTradingInfoUrl: KOSDAQ 매매 정보 API URL</li>
 * </ul>
 *
 * @author RSIranking Team
 * @version 1.0
 */
@Component
@NoArgsConstructor
@AllArgsConstructor
@ConfigurationProperties(prefix = "api.krx")
@Getter
@Setter
public class KrxApiProperties {
    /** API 인증 키 */
    private String key;
    /** KOSPI 종목 정보 API URL */
    private String kospiInfoUrl;
    /** KOSDAQ 종목 정보 API URL */
    private String kosdaqInfoUrl;
    /** KOSPI 일별 매매 정보 API URL */
    private String kospiTradingInfoUrl;
    /** KOSDAQ 일별 매매 정보 API URL */
    private String kosdaqTradingInfoUrl;
}
