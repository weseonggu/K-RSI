package com.service.RSIranking.service;

import com.service.RSIranking.entity.SecuritiesStockEntity;
import com.service.RSIranking.repository.SecuritiesStockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SecuritiesStockService {

    private static final String API_URL = "http://data-dbg.krx.co.kr/svc/apis/sto/stk_bydd_trd";
    private static final String AUTH_KEY = "3DAF2BE024974AD1B472C1F214E2A63D376D41E0";

    private final SecuritiesStockRepository securitiesStockRepository;
    private RestTemplate restTemplate;

    public List<SecuritiesStockEntity> getStockData(String date) {
        // API URL 조립
        String url = UriComponentsBuilder.fromHttpUrl(API_URL)
                .queryParam("basDd", date) // 기준 날짜 추가
                .toUriString();

        // HTTP 헤더 설정
        HttpHeaders headers = new HttpHeaders();
        headers.set("AUTH_KEY", AUTH_KEY);
        headers.set("Accept", "application/json");

        HttpEntity<String> entity = new HttpEntity<>(headers);

        // API 요청
        ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);

        List<SecuritiesStockEntity> stocks = new ArrayList<>();

        // 응답 데이터 파싱
        if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
            Map<String, Object> body = response.getBody();
            if (body.containsKey("OutBlock_1")) {
                List<Map<String, Object>> stockList = (List<Map<String, Object>>) body.get("OutBlock_1");

                for (Map<String, Object> stock : stockList) {
                    stocks.add(SecuritiesStockEntity.parseJsonToSecuritiesStock(stock));
                }
            }
        }

        // DB에 저장
        securitiesStockRepository.saveAll(stocks);
        return stocks;
    }
}
