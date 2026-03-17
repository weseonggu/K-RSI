package com.service.RSIranking.batch.rsi_calculation_job.step;

import com.service.RSIranking.dto.RSIMessageDTO;

import com.service.RSIranking.entity.inter.StockInfoEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.annotation.BeforeStep;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * RSI 계산 메시지 생성 Processor.
 *
 * <p>종목 정보 엔티티를 RSI 계산에 필요한 메시지 DTO로 변환합니다.
 * Job 파라미터로부터 대상 날짜와 과거 14일 장 날짜 정보를 받아 메시지를 구성합니다.</p>
 *
 * <h2>메시지 구성 요소</h2>
 * <ul>
 *   <li>종목 코드 (isu_cd)</li>
 *   <li>대상 날짜 (targetDate)</li>
 *   <li>시장 구분 (mkt_nm)</li>
 *   <li>과거 14일 장 날짜 목록 (marketDate)</li>
 * </ul>
 *
 * @author RSIranking Team
 * @version 1.1
 * @see RSIMessageDTO
 */
@StepScope
@Component
@RequiredArgsConstructor
@Slf4j
public class RSIMessageMakeProcess implements ItemProcessor<StockInfoEntity, RSIMessageDTO> {

    private String targetDate;
    private String mkt_nm;
    private List<LocalDate> marketDate;
    private StepExecution stepExecution;

    /**
     * Step 실행 전 Job 파라미터로부터 필요한 정보를 추출합니다.
     *
     * @param stepExecution Step 실행 정보
     */
    @BeforeStep
    public void retrieveMarketDate(StepExecution stepExecution) {
        this.stepExecution = stepExecution;
        JobParameters jobParameters = stepExecution.getJobParameters();
        this.targetDate = jobParameters.getString("targetDate");
        this.mkt_nm = jobParameters.getString("mktNm");
        this.marketDate = reverseSerialization(jobParameters.getString("marketDayList"));
    }

    /**
     * 종목 정보를 RSI 계산 메시지 DTO로 변환합니다.
     *
     * @param item 종목 정보 엔티티
     * @return RSI 계산 메시지 DTO
     * @throws Exception 처리 중 예외 발생 시
     */
    @Override
    public RSIMessageDTO process(StockInfoEntity item) throws Exception {
        return new RSIMessageDTO(item.getId(), this.targetDate, this.mkt_nm, this.marketDate);
    }

    /**
     * Job 파라미터의 장 날짜 문자열을 LocalDate 리스트로 역직렬화합니다.
     *
     * @param marketDate 쉼표로 구분된 날짜 문자열 (yyyyMMdd)
     * @return LocalDate 리스트
     */
    private List<LocalDate> reverseSerialization(String marketDate) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd");
        return Arrays.stream(marketDate.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(dateStr -> LocalDate.parse(dateStr, formatter))
                .collect(Collectors.toList());
    }
}
