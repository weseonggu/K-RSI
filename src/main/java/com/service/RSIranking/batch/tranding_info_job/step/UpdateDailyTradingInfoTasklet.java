package com.service.RSIranking.batch.tranding_info_job.step;

import com.fasterxml.jackson.core.type.TypeReference;
import com.service.RSIranking.dto.TradingInfoDto;
import com.service.RSIranking.entity.DailyTradingInformation;
import com.service.RSIranking.service.InterStepDataSharingWithRedisService;
import com.service.RSIranking.service.UpdateDailyTradingInfoService;
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
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;
@Component
@RequiredArgsConstructor
@Slf4j
public class UpdateDailyTradingInfoTasklet implements Tasklet {

    private List<TradingInfoDto> tradingInfoDtos;

    private final InterStepDataSharingWithRedisService interStepDataSharingWithRedis;
    private final UpdateDailyTradingInfoService updateDailyTradingInfoService;

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
        // todo 매매정보 업데이트 로직 try-catch 사용하기
        List<DailyTradingInformation> dailyTradingInformationList =  tradingInfoDtos.stream()
                .map(DailyTradingInformation :: new)
                .collect(Collectors.toList());
        updateDailyTradingInfoService.tradingInfoInsert(dailyTradingInformationList, tradingInfoDtos);
        return RepeatStatus.FINISHED;
    }
}
