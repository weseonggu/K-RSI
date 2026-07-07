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

/**
 * KRX API에서 종목 정보를 가져오는 Tasklet.
 *
 * <p>한국거래소 Open API를 호출하여 KOSPI/KOSDAQ 종목 정보를 조회하고,
 * 다음 Step에서 사용할 수 있도록 Redis에 임시 저장합니다.</p>
 *
 * <h2>처리 흐름</h2>
 * <ol>
 *   <li>KRX API 호출 (재시도 포함)</li>
 *   <li>응답 데이터 DTO 변환</li>
 *   <li>Redis 임시 저장</li>
 *   <li>Job ExecutionContext에 Redis 키 저장</li>
 * </ol>
 *
 * <h2>종료 상태</h2>
 * <ul>
 *   <li>NO_DATA: API 응답에 데이터 없음</li>
 *   <li>REDIS_FAILED: Redis 저장 실패</li>
 * </ul>
 *
 * @author RSIranking Team
 * @version 1.0
 */
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

    /**
     * KRX API를 호출하여 종목 정보를 가져옵니다.
     *
     * @param contribution  Step 기여 정보
     * @param chunkContext  청크 컨텍스트
     * @return 작업 완료 상태
     * @throws Exception 처리 중 예외 발생 시
     */
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
        // null 체크를 먼저 수행해야 NPE 발생 안 함
        if (response == null || response.getStatusCode() != HttpStatus.OK || response.getBody() == null ||
                !response.getBody().containsKey("OutBlock_1")) {
            log.warn("{}: KRX 응답 비정상 (response={}, statusCode={}) - 스킵",
                    mktNM, response,
                    response == null ? "null" : response.getStatusCode());
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

    /**
     * Step 실행 전 Job 파라미터에서 API 설정 정보를 추출합니다.
     *
     * @param stepExecution Step 실행 정보
     */
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
