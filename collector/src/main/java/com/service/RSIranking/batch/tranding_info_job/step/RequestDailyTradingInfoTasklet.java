package com.service.RSIranking.batch.tranding_info_job.step;

import com.service.RSIranking.config.krx_api.ApiConfig;
import com.service.RSIranking.dto.*;
import com.service.RSIranking.service.InterStepDataSharingWithRedisService;
import com.service.RSIranking.service.KrxRequestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.*;
import org.springframework.batch.core.annotation.BeforeStep;
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
 * KRX API에서 일별 매매 정보를 요청하는 Tasklet.
 *
 * <p>한국거래소 Open API를 호출하여 일별 매매 정보를 조회하고,
 * 다음 Step에서 사용할 수 있도록 Redis에 임시 저장합니다.</p>
 *
 * <h2>수집 정보</h2>
 * <ul>
 *   <li>기준일자 (basDd)</li>
 *   <li>종목코드/종목명</li>
 *   <li>종가/시가/고가/저가</li>
 *   <li>대비/등락률</li>
 *   <li>거래량/거래대금</li>
 * </ul>
 *
 * @author RSIranking Team
 * @version 1.0
 */
@StepScope
@Component
@RequiredArgsConstructor
@Slf4j
public class RequestDailyTradingInfoTasklet implements Tasklet {

    private StepExecution stepExecution;
    private ApiConfig apiConfig =  new ApiConfig();
    private String mktNM;
    private String date;

    private final KrxRequestService krxRequestService;
    private final InterStepDataSharingWithRedisService interStepDataSharingWithRedisService;


    /**
     * KRX API를 호출하여 일별 매매 정보를 가져옵니다.
     *
     * @param contribution  Step 기여 정보
     * @param chunkContext  청크 컨텍스트
     * @return 작업 완료 상태
     * @throws Exception 처리 중 예외 발생 시
     */
    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {

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
        // todo Stock 인터 페이스 변경
        List<TradingInfoDto> stocks = new ArrayList<>();

        // 데이터 변환 및 저장
        for (Map<String, Object> stockJson : stockList) {
            TradingInfoDto dto = switch (mktNM.toUpperCase()) {
                case "KOSPI" -> KospiTradingInfoDto.fromJson(stockJson);
                case "KOSDAQ" -> KosdaqTradingInfoDto.fromJson(stockJson);
                case "ETF" -> EtfTradingInfoDto.fromJson(stockJson);
                default -> throw new IllegalArgumentException("지원하지 않는 시장: " + mktNM);
            };
            if (dto instanceof EtfTradingInfoDto etf && !etf.hasRequiredValues()) {
                log.warn("ETF 필수 일봉 값 누락으로 행 스킵 - date={}, isuCd={}", etf.getBasDd(), etf.getIsuCd());
                continue;
            }
            stocks.add(dto);
        }
        if (stocks.isEmpty()) {
            stepExecution.setExitStatus(new ExitStatus("NO_DATA"));
            return RepeatStatus.FINISHED;
        }
        // 레디스 키 날짜 + 시장
        String redisKey ="daily-trading-"+LocalDate.now().toString() + "-" + mktNM;
        
        // 레디스 저장 재시도 로직 있음 -> 재시도에 실패시 배치 종료
        if(interStepDataSharingWithRedisService.putStockToRedis(redisKey, stocks)){
            jobContext.put("DailyTradingInfo", redisKey);
        }else{
            stepExecution.setExitStatus(new ExitStatus("REDIS_FAILED"));
            return RepeatStatus.FINISHED;
        }
        
        return RepeatStatus.FINISHED;
    }

    /**
     * Step 실행 전 Job 파라미터에서 API 설정 정보를 추출합니다.
     *
     * @param stepExecution Step 실행 정보
     */
    @BeforeStep
    public void saveStepExecution(StepExecution stepExecution) {

        this.stepExecution = stepExecution;

        JobParameters jobParameters = stepExecution.getJobParameters();
        this.apiConfig.setUrl(jobParameters.getString("apiUrl"));
        this.apiConfig.setKey(jobParameters.getString("apiKey"));
        this.mktNM = jobParameters.getString("mktNm");
        this.date = jobParameters.getString("yesterday");


    }
}
