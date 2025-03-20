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

@Service
@Slf4j
public class KrxRequestService {

    /**
     * KRX API 요청
     * @param apiConfig 요청 api, key 객체
     * @param date 요청할 날짜
     * @return 응답
     */
    @Retryable(value = RestClientException.class, maxAttempts = 3, backoff = @Backoff(delay = 2000))
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

    @Recover
    public ResponseEntity<Map> recover(RestClientException e) {
        log.info("데이터 가져오기 실패");
        return null;
    }
}
