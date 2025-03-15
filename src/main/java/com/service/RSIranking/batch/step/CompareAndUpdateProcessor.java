package com.service.RSIranking.batch.step;

import com.service.RSIranking.dto.StockDto;
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
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;


@RequiredArgsConstructor
public class CompareAndUpdateProcessor implements ItemProcessor<SecuritiesStockEntity, SecuritiesStockEntity> {

    private List<StockDto> dtoList;
    private String redisKey;
    private final SecuritiesStockRepository securitiesStockRepository;
    private final RedisTemplate redisTemplate;

    @BeforeStep
    public void retrieveInterStepData(StepExecution stepExecution) {
        final JobExecution jobExecution = stepExecution.getJobExecution();
        final ExecutionContext jobContext = jobExecution.getExecutionContext();
        this.redisKey = (String)jobContext.get("StockDtoList");

        ValueOperations<String, List<StockDto>> ops = redisTemplate.opsForValue();
        this.dtoList = ops.get(redisKey);
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


//        System.out.println("Processor 반환 데이터: " + entity);
        return entity;
    }

    @AfterStep
    public ExitStatus collectNewStocks() {
//        System.out.println("=============================새로운 데이터 저장====================");
        List<StockDto> newStockDtos = dtoList.stream()
                .filter(dto -> !dto.isChecked()) // 확인되지 않은 DTO (신규 데이터)
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
