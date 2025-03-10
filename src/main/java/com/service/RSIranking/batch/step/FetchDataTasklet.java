package com.service.RSIranking.batch.step;

import com.service.RSIranking.config.krx_api.ApiConfig;
import com.service.RSIranking.dto.SecuritiesStockDto;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.annotation.BeforeStep;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
public class FetchDataTasklet implements Tasklet {

    private StepExecution stepExecution;
    private ApiConfig apiConfig;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        JobExecution jobExecution = contribution.getStepExecution().getJobExecution();
        ExecutionContext jobContext = jobExecution.getExecutionContext();

        // 오늘 날짜
        String presentDate = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));

        // API URL 조립
        String url = UriComponentsBuilder.fromHttpUrl(apiConfig.getUrl())
//                .queryParam("basDd", presentDate) // 기준 날짜 추가
                .queryParam("basDd", "20250307")
                .toUriString();

        RestTemplate restTemplate = new RestTemplate();

        // HTTP 헤더 설정
        HttpHeaders headers = new HttpHeaders();
        headers.set("AUTH_KEY", apiConfig.getKey());
        headers.set("Accept", "application/json");

        HttpEntity<String> entity = new HttpEntity<>(headers);

        // API 요청
        ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);

        List<SecuritiesStockDto> stocks = new ArrayList<>();

        // 응답 데이터 파싱
        if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
            Map<String, Object> body = response.getBody();
            if (body.containsKey("OutBlock_1")) {
                List<Map<String, Object>> stockList = (List<Map<String, Object>>) body.get("OutBlock_1");

                for (Map<String, Object> stockJson : stockList) {
                    // json -> DTO로 변환 후 리스트에 적제
                    stocks.add(SecuritiesStockDto.fromJson(stockJson, true));
                }
            }
        }

        jobContext.put("StockDtoList", stocks);

        return RepeatStatus.FINISHED;
    }

    @BeforeStep
    public void saveStepExecution(StepExecution stepExecution) {

        this.stepExecution = stepExecution;

        JobParameters jobParameters = stepExecution.getJobParameters();
        String apiUrl = jobParameters.getString("apiUrl");
        String apiKey = jobParameters.getString("apiKey");
        if (apiUrl != null && apiKey != null) {
            this.apiConfig = new ApiConfig(apiKey, apiUrl);
        }
    }
}
