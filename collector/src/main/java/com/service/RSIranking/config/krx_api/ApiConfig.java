package com.service.RSIranking.config.krx_api;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * KRX API 설정 정보를 담는 POJO 클래스.
 *
 * <p>API 호출 시 필요한 URL과 인증 키 정보를 저장합니다.</p>
 *
 * @author RSIranking Team
 * @version 1.0
 */
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class ApiConfig {
    /** API 인증 키 */
    private String key;
    /** API 엔드포인트 URL */
    private String url;
}
