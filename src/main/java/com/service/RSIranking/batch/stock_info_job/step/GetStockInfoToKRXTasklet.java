package com.service.RSIranking.batch.stock_info_job.step;

import com.service.RSIranking.config.krx_api.ApiConfig;
import com.service.RSIranking.dto.KosdaqSecuritiesStockDto;
import com.service.RSIranking.dto.KospiSecuritiesStockDto;
import com.service.RSIranking.dto.StockDto;
import com.service.RSIranking.service.InterStepDataSharingWithRedisService;
import com.service.RSIranking.service.KrxRequestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.*;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
@StepScope
@Component
@RequiredArgsConstructor
@Slf4j
public class GetStockInfoToKRXTasklet implements Tasklet, StepExecutionListener {

    private StepExecution stepExecution;
    private ApiConfig apiConfig =  new ApiConfig();
    private String mktNM;
    private String date;

    private final KrxRequestService krxRequestService;
    private final InterStepDataSharingWithRedisService interStepDataSharingWithRedisService;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {

        log.info(mktNM + ": 데이터 KRX API 요청");

        JobExecution jobExecution = contribution.getStepExecution().getJobExecution();
        ExecutionContext jobContext = jobExecution.getExecutionContext();

        // 요청 서비스 호출
        // 재시도로 3회 실패시 null을 반환하며 배치를 종료하도록 밑에서 구성
        ResponseEntity<Map> response = krxRequestService.krxRequest(apiConfig, date);

        // 응답 데이터 확인
        // response이 null 이면 스탭 종료밑 배치 종료
        if (response.getStatusCode() != HttpStatus.OK || response.getBody() == null || response == null ||
                !response.getBody().containsKey("OutBlock_1")) {
            stepExecution.setExitStatus(new ExitStatus("NO_DATA"));
            return RepeatStatus.FINISHED; // 데이터가 없으면 배치를 종료
        }

        List<Map<String, Object>> stockList = (List<Map<String, Object>>) response.getBody().get("OutBlock_1");

        // 응답은 왔지만 데이터가 비어 있는경우 스탭과 배치 종료
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
        // 레디스 키 날짜 + 시장
        String redisKey = LocalDate.now().toString() + "-" + mktNM;

        // 레디스 저장 재시도 로직 있음 -> 재시도에 실패시 배치 종료
        if(interStepDataSharingWithRedisService.putStockToRedis(redisKey, stocks)){
            jobContext.put("StockDtoList", redisKey);
        }else{
            stepExecution.setExitStatus(new ExitStatus("REDIS_FAILED"));
            stepExecution.setStatus(BatchStatus.FAILED);
            throw new RuntimeException("Redis 저장 실패");
        }
        log.info(mktNM + ": 데이터 Redis 임지 저장 완료");
        return RepeatStatus.FINISHED;
    }

    @Override
    public void beforeStep(StepExecution stepExecution) {

        this.stepExecution = stepExecution;

        JobParameters jobParameters = stepExecution.getJobParameters();
        this.apiConfig.setUrl(jobParameters.getString("apiUrl"));
        this.apiConfig.setKey(jobParameters.getString("apiKey"));
        this.mktNM = jobParameters.getString("mktNm");
        this.date = jobParameters.getString("yesterday");

    }
}
