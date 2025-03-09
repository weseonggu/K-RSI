package com.service.RSIranking.batch.step;

import com.service.RSIranking.dto.SecuritiesStockDto;
import com.service.RSIranking.entity.SecuritiesStockEntity;
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

    private List<SecuritiesStockDto> dtoList;

    @BeforeStep
    public void retrieveInterStepData(StepExecution stepExecution) {
        final JobExecution jobExecution = stepExecution.getJobExecution();
        final ExecutionContext jobContext = jobExecution.getExecutionContext();
        this.dtoList = (List<SecuritiesStockDto>) jobContext.get("StockDtoList");
    }

    @Override
    public SecuritiesStockEntity process(SecuritiesStockEntity entity) throws Exception {
        // Todo 데이터를 비교해서 삭제된거는 반영이되지만 새로 추가된 종목은 저장이 안되는 문제가 있음
        // dtoList와 비교하여 변경 사항 처리
        Optional<SecuritiesStockDto> matchedDto = dtoList.stream()
                .filter(dto -> dto.getIsuCd().equals(entity.getId()))
                .findFirst();

        if (matchedDto.isPresent()) {
            // 기존 데이터 업데이트
            entity.updateFromDto(matchedDto.get());
            // 확인한 dto true로 변경
            matchedDto.get().updateChecked();
        } else {
            // 삭제된 데이터 처리
            entity.delistStock();
        }


        System.out.println("Processor 반환 데이터: " + entity);
        return entity;
    }

    @AfterStep
    public ExitStatus collectNewStocks(StepExecution stepExecution) {
        List<SecuritiesStockDto> newStockDtos = dtoList.stream()
                .filter(dto -> !dto.isChecked()) // 🔹 확인되지 않은 DTO (신규 데이터)
                .collect(Collectors.toList());

        // 🔹 ExecutionContext에 신규 데이터 저장
        stepExecution.getJobExecution().getExecutionContext().put("newStockDtos", newStockDtos);

        return ExitStatus.COMPLETED;
    }

}
