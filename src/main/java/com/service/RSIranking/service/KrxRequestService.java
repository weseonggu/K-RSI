package com.service.RSIranking.service;

import com.service.RSIranking.config.krx_api.ApiConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

/**
 * 한국거래소(KRX) API 요청 서비스.
 *
 * <p>KRX Open API를 통해 종목 정보 및 일별 매매 정보를 요청하는 서비스입니다.
 * API 요청 실패 시 최대 3회까지 재시도합니다.</p>
 *
 * <h2>재시도 정책</h2>
 * <ul>
 *   <li>최대 재시도 횟수: 3회</li>
 *   <li>재시도 간격: 2초</li>
 *   <li>재시도 대상 예외: {@link RestClientException}</li>
 * </ul>
 *
 * @author RSIranking Team
 * @version 1.0
 */
@Service
@Slf4j
public class KrxRequestService {

    /**
     * KRX API 요청
     * @param apiConfig 요청 api, key 객체
     * @param date 요청할 날짜
     * @return 응답
     */
    @Retryable(retryFor = RestClientException.class, maxAttempts = 3, backoff = @Backoff(delay = 2000))
    public ResponseEntity<Map>  krxRequest(ApiConfig apiConfig, String date){

        RestTemplate restTemplate = new RestTemplate();

        // API URL 조립
        String url = UriComponentsBuilder.fromHttpUrl(apiConfig.getUrl())
                .queryParam("basDd", date) // 테스트 날짜
                .toUriString();

        // HTTP 헤더 설정
        HttpHeaders headers = new HttpHeaders();
        headers.set("AUTH_KEY", apiConfig.getKey());
        headers.set("Accept", "application/json");

        HttpEntity<String> entity = new HttpEntity<>(headers);

        // API 요청
        ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);

        return response;
    }

    /**
     * KRX API 요청 재시도 실패 시 호출되는 복구 메서드.
     *
     * <p>3회 재시도 후에도 실패한 경우 null을 반환합니다.</p>
     *
     * @param e         발생한 예외
     * @param apiConfig API 설정 정보
     * @param date      요청 날짜
     * @return null (실패 시)
     */
    @Recover
    public ResponseEntity<Map> recover(RestClientException e, ApiConfig apiConfig, String date) {
        log.info("데이터 가져오기 실패");
        return null;
    }
}
