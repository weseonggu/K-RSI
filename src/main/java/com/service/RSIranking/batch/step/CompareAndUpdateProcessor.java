package com.service.RSIranking.batch.step;

import com.service.RSIranking.dto.SecuritiesStockDto;
import com.service.RSIranking.entity.SecuritiesStockEntity;
import com.service.RSIranking.repository.SecuritiesStockRepository;
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
    private final SecuritiesStockRepository securitiesStockRepository;

    @BeforeStep
    public void retrieveInterStepData(StepExecution stepExecution) {
        final JobExecution jobExecution = stepExecution.getJobExecution();
        final ExecutionContext jobContext = jobExecution.getExecutionContext();
        this.dtoList = (List<SecuritiesStockDto>) jobContext.get("StockDtoList");
    }

    @Override
    public SecuritiesStockEntity process(SecuritiesStockEntity entity) throws Exception {
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
    public ExitStatus collectNewStocks() {
        List<SecuritiesStockDto> newStockDtos = dtoList.stream()
                .filter(dto -> !dto.isChecked()) // 🔹 확인되지 않은 DTO (신규 데이터)
                .collect(Collectors.toList());

        // DB에 신규 데이터 저장
        List<SecuritiesStockEntity> newStockEntities = newStockDtos.stream()
                .map(SecuritiesStockEntity::new) // DTO -> Entity 변환
                .collect(Collectors.toList());

        if (!newStockEntities.isEmpty()) {
            securitiesStockRepository.saveAll(newStockEntities);
        }

        return ExitStatus.COMPLETED;
    }

}
