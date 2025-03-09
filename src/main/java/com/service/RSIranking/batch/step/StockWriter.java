package com.service.RSIranking.batch.step;

import com.service.RSIranking.dto.SecuritiesStockDto;
import com.service.RSIranking.entity.SecuritiesStockEntity;
import com.service.RSIranking.repository.SecuritiesStockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.scope.context.StepSynchronizationManager;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemWriter;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public class StockWriter implements ItemWriter<SecuritiesStockEntity> {

    private final SecuritiesStockRepository securitiesStockRepository;


    @Override
    public void write(Chunk<? extends SecuritiesStockEntity> chunk) throws Exception {
        ExecutionContext jobContext = StepSynchronizationManager.getContext().getStepExecution().getJobExecution().getExecutionContext();
        List<SecuritiesStockDto> newStockDtos = (List<SecuritiesStockDto>) jobContext.get("newStockDtos");

        if (newStockDtos == null) {
            newStockDtos = new ArrayList<>(); // null일 경우 빈 리스트를 생성
        }

        List<SecuritiesStockEntity> newStockEntities = newStockDtos.stream()
                .map(SecuritiesStockEntity::new) // DTO -> Entity 변환
                .collect(Collectors.toList());

        // 기존 데이터 + 신규 데이터 함께 저장
        if (!newStockEntities.isEmpty()) {
            securitiesStockRepository.saveAll(newStockEntities);
        }

        System.out.println("📝 Writer 실행! 저장할 데이터: " + chunk);
        try {
            securitiesStockRepository.saveAll(chunk);  // 실제 DB 저장
            System.out.println("✅ Writer 저장 완료!");
        } catch (Exception e) {
            System.err.println("❌ Writer에서 예외 발생: " + e.getMessage());
            throw e;
        }
    }

}
