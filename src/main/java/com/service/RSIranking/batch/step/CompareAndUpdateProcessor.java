package com.service.RSIranking.batch.step;

import com.fasterxml.jackson.core.type.TypeReference;
import com.service.RSIranking.dto.StockDto;
import com.service.RSIranking.entity.SecuritiesStockEntity;
import com.service.RSIranking.service.InterStepDataSharingWithRedisService;
import com.service.RSIranking.service.StockBulkInsertService;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.annotation.AfterStep;
import org.springframework.batch.core.annotation.BeforeStep;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemProcessor;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;


@RequiredArgsConstructor
public class CompareAndUpdateProcessor implements ItemProcessor<SecuritiesStockEntity, SecuritiesStockEntity> {

    private List<StockDto> dtoList;


    private final InterStepDataSharingWithRedisService interStepDataSharingWithRedisService;
    private final StockBulkInsertService stockBulkInsertService;

    @BeforeStep
    public void retrieveInterStepData(StepExecution stepExecution) {
        final JobExecution jobExecution = stepExecution.getJobExecution();
        final ExecutionContext jobContext = jobExecution.getExecutionContext();
        String redisKey = (String)jobContext.get("StockDtoList");
        this.dtoList = interStepDataSharingWithRedisService.getStockToRedis(redisKey, new TypeReference<List<StockDto>>() {});
    }

    @Override
    public SecuritiesStockEntity process(SecuritiesStockEntity entity) throws Exception {
        // dtoList와 비교하여 변경 사항 처리
        Optional<StockDto> matchedDto = dtoList.stream()
                .filter(dto -> dto.getIsuCd().equals(entity.getId()))
                .findFirst();

        if (matchedDto.isPresent()) {
            // 기존 데이터 업데이트
            entity.updateFromDto(matchedDto.get());
            // 확인한 dto true로 변경
            matchedDto.ifPresent(StockDto::updateChecked);
        } else {
            // 삭제된 데이터 처리
            entity.delistStock();
        }
        return entity;
    }

    // jdbc를 사용한 신규 종목 저장
    @AfterStep
    public ExitStatus collectNewStocks(StepExecution stepExecution) {

        // 이전 작업에서 문제가 발생할 경우 그냥 종료
        if (stepExecution.getExitStatus().getExitCode().equals(ExitStatus.FAILED.getExitCode())) {
            System.out.println("========write에서 실패===========");
            return ExitStatus.FAILED;
        }
        System.out.println("========DB저장===========");
        List<StockDto> newStockDtos = dtoList.stream()
                .filter(dto -> !dto.isChecked()) // 확인되지 않은 DTO (신규 데이터)
                .collect(Collectors.toList());

        // DB에 신규 데이터 저장
        List<SecuritiesStockEntity> newStockEntities = newStockDtos.stream()
                .map(SecuritiesStockEntity::new) // DTO -> Entity 변환
                .collect(Collectors.toList());

        if (!newStockEntities.isEmpty()) {
            stockBulkInsertService.stocksInsert(newStockEntities);
        }
        return ExitStatus.COMPLETED;
    }

}
