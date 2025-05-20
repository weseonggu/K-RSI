package com.service.RSIranking.batch.step;

import com.fasterxml.jackson.core.type.TypeReference;
import com.service.RSIranking.dto.TradingInfoDto;
import com.service.RSIranking.service.InterStepDataSharingWithRedisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.annotation.BeforeStep;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.repeat.RepeatStatus;

import java.util.List;

@RequiredArgsConstructor
@Slf4j
public class UpdateDailyTradingInfoTasklet implements Tasklet {

    private List<TradingInfoDto> tradingInfoDtos;

    private final InterStepDataSharingWithRedisService interStepDataSharingWithRedis;

    @BeforeStep
    public void retrieveInterStepData(StepExecution stepExecution){
        try {
            final JobExecution jobExecution = stepExecution.getJobExecution();
            final ExecutionContext jobContext = jobExecution.getExecutionContext();
            String redisKey = (String) jobContext.get("DailyTradingInfo");

            this.tradingInfoDtos = interStepDataSharingWithRedis
                    .getStockToRedis(redisKey, new TypeReference<List<TradingInfoDto>>() {})
                    .orElseThrow(() -> new RuntimeException("Redis에서 TradingInfoDtoList를 찾을 수 없습니다."));
        } catch (Exception e) {
            throw new RuntimeException("중간 단계 데이터 조회 중 예외 발생", e);
        }
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {

        log.info(tradingInfoDtos.toString());

        return RepeatStatus.FINISHED;
    }
}
