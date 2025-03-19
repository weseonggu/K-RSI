package com.service.RSIranking.batch.step;

import com.service.RSIranking.config.krx_api.ApiConfig;
import com.service.RSIranking.dto.KosdaqSecuritiesStockDto;
import com.service.RSIranking.dto.KospiSecuritiesStockDto;
import com.service.RSIranking.dto.StockDto;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.*;
import org.springframework.batch.core.annotation.BeforeStep;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
public class FetchDataTasklet implements Tasklet {

    private StepExecution stepExecution;
    private ApiConfig apiConfig =  new ApiConfig();

    private final RedisTemplate redisTemplate;
    private String mktNM;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        JobExecution jobExecution = contribution.getStepExecution().getJobExecution();
        ExecutionContext jobContext = jobExecution.getExecutionContext();

        // 어제 날짜
        String presentDate = LocalDate.now().minusDays(1).format(DateTimeFormatter.ofPattern("yyyyMMdd"));

        // API URL 조립
        String url = UriComponentsBuilder.fromHttpUrl(apiConfig.getUrl())
                .queryParam("basDd", "20250316") // 테스트 날짜
                .toUriString();

        RestTemplate restTemplate = new RestTemplate();

        // HTTP 헤더 설정
        HttpHeaders headers = new HttpHeaders();
        headers.set("AUTH_KEY", apiConfig.getKey());
        headers.set("Accept", "application/json");

        HttpEntity<String> entity = new HttpEntity<>(headers);

        // API 요청
        ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);

        // 응답 데이터 확인
        if (response.getStatusCode() != HttpStatus.OK || response.getBody() == null ||
                !response.getBody().containsKey("OutBlock_1")) {
            stepExecution.setExitStatus(ExitStatus.NOOP);
            return RepeatStatus.FINISHED; // 데이터가 없으면 배치를 종료
        }

        List<Map<String, Object>> stockList = (List<Map<String, Object>>) response.getBody().get("OutBlock_1");
        if (stockList == null || stockList.isEmpty()) {

            stepExecution.setExitStatus(new ExitStatus("NO_DATA"));
            return RepeatStatus.FINISHED; // 데이터가 없으면 배치를 종료
        }

        List<StockDto> stocks = new ArrayList<>();

        // 데이터 변환 및 저장
        for (Map<String, Object> stockJson : stockList) {
            if ("KOSDAQ".equalsIgnoreCase(mktNM)) {
                stocks.add(KosdaqSecuritiesStockDto.fromJson(stockJson, true));
            } else {
                stocks.add(KospiSecuritiesStockDto.fromJson(stockJson, true));
            }
        }

        String redisKey = LocalDate.now().toString() + "-" + mktNM;
        ValueOperations<String, List<StockDto>> ops = redisTemplate.opsForValue();
        ops.set(redisKey, stocks, Duration.ofHours(3));

        jobContext.put("StockDtoList", redisKey);

        return RepeatStatus.FINISHED;
    }

    @BeforeStep
    public void saveStepExecution(StepExecution stepExecution) {

        this.stepExecution = stepExecution;

        JobParameters jobParameters = stepExecution.getJobParameters();
        this.apiConfig.setUrl(jobParameters.getString("apiUrl"));
        this.apiConfig.setKey(jobParameters.getString("apiKey"));
        this.mktNM = jobParameters.getString("mktNm");

    }
}
