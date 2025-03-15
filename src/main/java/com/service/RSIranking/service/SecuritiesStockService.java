package com.service.RSIranking.service;

import com.service.RSIranking.config.krx_api.KrxApiProperties;
import com.service.RSIranking.dto.KospiSecuritiesStockDto;
import com.service.RSIranking.entity.SecuritiesStockEntity;
import com.service.RSIranking.repository.SecuritiesStockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SecuritiesStockService {

    private final KrxApiProperties krxApiProperties;

    private final SecuritiesStockRepository securitiesStockRepository;


    public List<SecuritiesStockEntity> getStockData(String date) {
        // API URL 조립
        String url = UriComponentsBuilder.fromHttpUrl(krxApiProperties.getKospiInfoUrl())
                .queryParam("basDd", date) // 기준 날짜 추가
                .toUriString();

        RestTemplate restTemplate = new RestTemplate();

        // HTTP 헤더 설정
        HttpHeaders headers = new HttpHeaders();
        headers.set("AUTH_KEY", krxApiProperties.getKey());
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

                for (Map<String, Object> stockJson : stockList) {
                    // json -> DTO로 변환
                    KospiSecuritiesStockDto stockDto = KospiSecuritiesStockDto.fromJson(stockJson, true);
                    // DTO -> entity로 변환
                    stocks.add(stockDto.toEntity());
                }
            }
        }

        // DB에 저장
        securitiesStockRepository.saveAll(stocks);
        return stocks;
    }


}
