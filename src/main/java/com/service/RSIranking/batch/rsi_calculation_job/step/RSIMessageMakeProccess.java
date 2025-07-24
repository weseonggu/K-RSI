package com.service.RSIranking.batch.rsi_calculation_job.step;

import com.service.RSIranking.dto.RSIMessageDTO;
import com.service.RSIranking.entity.StockInfoEntity;
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
@StepScope
@Component
@RequiredArgsConstructor
@Slf4j
public class RSIMessageMakeProccess implements ItemProcessor<StockInfoEntity, RSIMessageDTO> {

    private String targetDate;
    private String mkt_nm;
    private List<LocalDate> marketDate;
    private StepExecution stepExecution;

    @BeforeStep
    public void retrieveMarketDate(StepExecution stepExecution){
        this.stepExecution = stepExecution;
        JobParameters jobParameters = stepExecution.getJobParameters();
        this.targetDate = jobParameters.getString("targetDate");
        this.mkt_nm = jobParameters.getString("mktNm");
        this.marketDate =reverseSerialization(jobParameters.getString("marketDayList"));
    }


    @Override
    public RSIMessageDTO process(StockInfoEntity item) throws Exception {
        RSIMessageDTO messageDTO = new RSIMessageDTO(item.getId(), this.targetDate, this.mkt_nm, this.marketDate);
        return messageDTO;
    }

    /**
     * 잡 파라미터 장날 문자열 역직렬화
     * @param marketDate
     * @return
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
