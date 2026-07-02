package com.service.RSIranking.batch.tranding_info_job.step;

import com.fasterxml.jackson.core.type.TypeReference;
import com.service.RSIranking.dto.TradingInfoDto;
import com.service.RSIranking.entity.KosdaqDailyTradingInformation;
import com.service.RSIranking.entity.KospiDailyTradingInformation;
import com.service.RSIranking.entity.inter.DailyTradingInformation;
import com.service.RSIranking.service.InterStepDataSharingWithRedisService;
import com.service.RSIranking.service.UpdateDailyTradingInfoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
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
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 일별 매매 정보를 데이터베이스에 저장하는 Tasklet.
 *
 * <p>Redis에 임시 저장된 일별 매매 정보를 읽어와 데이터베이스에 저장합니다.
 * 비동기 병렬 처리를 통해 대량의 데이터를 효율적으로 저장합니다.</p>
 *
 * <h2>병렬 처리</h2>
 * <ul>
 *   <li>배치 크기: 100개 단위</li>
 *   <li>비동기 처리: CompletableFuture 사용</li>
 *   <li>실패 시 롤백 처리</li>
 * </ul>
 *
 * @author RSIranking Team
 * @version 1.0
 */
@StepScope
@Component
@RequiredArgsConstructor
@Slf4j
public class UpdateDailyTradingInfoTasklet implements Tasklet {

    private List<TradingInfoDto> tradingInfoDtos;
    private String mktNm;

    private final InterStepDataSharingWithRedisService interStepDataSharingWithRedis;
    private final UpdateDailyTradingInfoService updateDailyTradingInfoService;

    /**
     * Step 실행 전 Redis에서 일별 매매 정보를 조회합니다.
     *
     * @param stepExecution Step 실행 정보
     */
    @BeforeStep
    public void retrieveInterStepData(StepExecution stepExecution){
        try {
            final JobExecution jobExecution = stepExecution.getJobExecution();
            final ExecutionContext jobContext = jobExecution.getExecutionContext();
            String redisKey = (String) jobContext.get("DailyTradingInfo");
            JobParameters jobParameters = stepExecution.getJobParameters();
            this.mktNm = jobParameters.getString("mktNm");

            this.tradingInfoDtos = interStepDataSharingWithRedis
                    .getStockToRedis(redisKey, new TypeReference<List<TradingInfoDto>>() {})
                    .orElseThrow(() -> new RuntimeException("Redis에서 TradingInfoDtoList를 찾을 수 없습니다."));
        } catch (Exception e) {
            throw new RuntimeException("중간 단계 데이터 조회 중 예외 발생", e);
        }
    }

    /**
     * 일별 매매 정보를 데이터베이스에 저장합니다.
     *
     * <p>100개 단위로 비동기 병렬 저장을 수행하며,
     * 실패 시 해당 날짜의 데이터를 롤백합니다.</p>
     *
     * @param contribution  Step 기여 정보
     * @param chunkContext  청크 컨텍스트
     * @return 작업 완료 상태
     * @throws Exception 처리 중 예외 발생 시
     */
    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        // 레디스에서 가져온 데이터 엔티티로 변환 (시장별 엔티티 타입만 다르고 로직은 동일)
        Function<TradingInfoDto, DailyTradingInformation> toEntity =
                "KOSPI".equals(mktNm) ? KospiDailyTradingInformation::new : KosdaqDailyTradingInformation::new;
        List<DailyTradingInformation> tradingInfoEntities = tradingInfoDtos.stream()
                .map(toEntity)
                .collect(Collectors.toList());

        // todo 병렬 작업할 데이터 수 정하기 설정 파일에서 값가져 오도록 변경하기
        int batchSize = 100;
        // 비동기 병렬 처리한 결과 저장
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        // 비동기 병렬 반복문
        for (int i = 0; i < tradingInfoEntities.size(); i += batchSize) {
            int end = Math.min(i + batchSize, tradingInfoEntities.size());
            List<DailyTradingInformation> subList = tradingInfoEntities.subList(i, end);
            List<TradingInfoDto> subDtoList = tradingInfoDtos.subList(i, end);
            futures.add(updateDailyTradingInfoService.tradingInfoInsert(subList, subDtoList, mktNm));
        }
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get();
            log.info("{}: 매매정보 병렬 저장 완료", mktNm);
        } catch (ExecutionException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            // 진짜 삽입 오류만 여기 도달한다 (중복은 멱등 처리되어 실패로 오지 않음).
            // 부분 적재 상태를 지우고 Step을 실패시켜 후속 RSI 계산이 불완전 데이터로 돌지 않게 한다.
            // (과거에는 로그만 남기고 Step이 성공 처리되어 데이터 누락이 은폐되었음)
            log.error("{}: 매매정보 비동기 저장 중 오류 발생, 해당 날짜 전체 롤백 후 Step 실패 처리", mktNm, e);
            updateDailyTradingInfoService.tradingInfoInsertRollback(tradingInfoDtos.get(0).getBasDd(), mktNm);
            throw new RuntimeException(mktNm + ": 매매정보 저장 실패 - 날짜: " + tradingInfoDtos.get(0).getBasDd(), e);
        }

        return RepeatStatus.FINISHED;
    }
}
