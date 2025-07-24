package com.service.RSIranking.batch.stock_info_job.step;

import com.fasterxml.jackson.core.type.TypeReference;
import com.service.RSIranking.dto.StockDto;

import com.service.RSIranking.entity.KospiStockInfoEntity;
import com.service.RSIranking.entity.inter.StockInfoEntity;
import com.service.RSIranking.service.InterStepDataSharingWithRedisService;
import com.service.RSIranking.service.StockBulkInsertService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.*;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@StepScope
@Component
@RequiredArgsConstructor
@Slf4j
public class CompareAndUpdateProcessor implements ItemProcessor<StockInfoEntity, StockInfoEntity>, StepExecutionListener {

    private List<StockDto> dtoList;


    private final InterStepDataSharingWithRedisService interStepDataSharingWithRedis;
    private final StockBulkInsertService stockBulkInsertService;


    @Override
    public void beforeStep(StepExecution stepExecution) {
        try {
            final JobExecution jobExecution = stepExecution.getJobExecution();
            final ExecutionContext jobContext = jobExecution.getExecutionContext();
            String redisKey = (String) jobContext.get("StockDtoList");

            this.dtoList = interStepDataSharingWithRedis
                    .getStockToRedis(redisKey, new TypeReference<List<StockDto>>() {})
                    .orElseThrow(() -> new RuntimeException("Redis에서 StockDtoList를 찾을 수 없습니다."));
        } catch (Exception e) {
            stepExecution.setStatus(BatchStatus.FAILED);
            throw new RuntimeException("중간 단계 데이터 조회 중 예외 발생", e);
        }
    }

    @Override
    public StockInfoEntity process(StockInfoEntity entity) throws Exception {
        // dtoList와 비교하여 변경 사항 처리
        // todo dtoList가 null일 경우의 처리를 해야 함
        Optional<StockDto> matchedDto = dtoList.stream()
                .filter(dto -> dto.getIsuCd().equals(entity.getId()))
                .findFirst();

        if (matchedDto.isPresent()) {
            // 확인한 dto true로 변경
            matchedDto.ifPresent(StockDto::updateChecked);

            if (compareStockData(entity, matchedDto.get())){
                // 기존 데이터 업데이트
                log.info(entity.getId() + "변경사항 업데이트");
                entity.updateFromDto(matchedDto.get());
            }
            else{
                log.info(entity.getId() + "변경사항 없음 null 반환");
                return null;
            }


            return entity;
        } else {
            // 삭제된 데이터 처리
            log.info(entity.getId()+" 상장폐지");
            entity.delistStock();
            return entity;
        }
    }
    private boolean compareStockData(StockInfoEntity entity, StockDto dto){
        return !entity.getIsuNm().equals(dto.getIsuNm());
    }

    // jdbc를 사용한 신규 종목 저장
    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {

        if (stepExecution.getExitStatus().getExitCode().equals(ExitStatus.FAILED.getExitCode())) {
            return ExitStatus.FAILED;
        }

        List<StockDto> newStockDtos = dtoList.stream()
                .filter(dto -> !dto.isChecked())
                .collect(Collectors.toList());
        // todo 변경 사항 있음 .map(KospiStockInfoEntity::new)
        List<StockInfoEntity> newStockEntities = newStockDtos.stream()
                .map(KospiStockInfoEntity::new)
                .collect(Collectors.toList());
        log.info("신규 종목 추가: "+ newStockEntities.size() + "개 추가");
        if (!newStockEntities.isEmpty()) {
            stockBulkInsertService.stocksInsert(newStockEntities);
        }

        return ExitStatus.COMPLETED;
    }

}
