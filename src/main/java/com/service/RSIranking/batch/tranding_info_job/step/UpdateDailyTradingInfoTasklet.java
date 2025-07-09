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
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@StepScope
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
        // 레디스에서 가져온 데이터 엔티티로 변환
        List<DailyTradingInformation> dailyTradingInformationList = tradingInfoDtos.stream()
                .map(DailyTradingInformation::new)
                .collect(Collectors.toList());
        // todo 병렬 작업할 데이터 수 정하기 설정 파일에서 값가져 오도록 변경하기
        int batchSize = 100;
        // 비동기 병렬 처리한 결과 저장
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        // 비동기 병렬 반복문
        for (int i = 0; i < dailyTradingInformationList.size(); i += batchSize) {
            int end = Math.min(i + batchSize, dailyTradingInformationList.size());
            List<DailyTradingInformation> subList = dailyTradingInformationList.subList(i, end);
            List<TradingInfoDto> subDtoList = tradingInfoDtos.subList(i, end);
            futures.add(updateDailyTradingInfoService.tradingInfoInsert(subList, subDtoList));
        }
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get();
            log.info("매매정보 병렬 저장 완료");
        } catch (ExecutionException | InterruptedException e) {
            // 예외 발생 시 전체 롤백
//            throw new RuntimeException("매매정보 비동기 저장 중 오류 발생, 전체 롤백", e);
            log.info("매매정보 비동기 저장 중 오류 발생, 전체 롤백");
            tradingInfoDtos.get(0).getBasDd();
            updateDailyTradingInfoService.tradingInfoInsertRollback(tradingInfoDtos.get(0).getBasDd());

        }
        return RepeatStatus.FINISHED;
    }
}
