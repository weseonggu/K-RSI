package com.service.RSIranking.batch.step;

import com.service.RSIranking.entity.SecuritiesStockEntity;
import com.service.RSIranking.repository.jpa.SecuritiesStockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
public class StockWriter implements ItemWriter<SecuritiesStockEntity> {

    private final SecuritiesStockRepository securitiesStockRepository;

    @Override
    @Transactional
    public void write(Chunk<? extends SecuritiesStockEntity> chunk) throws Exception {

        try {
            securitiesStockRepository.saveAll(chunk);  // 실제 DB 저장
        } catch (Exception e) {
            throw e;
        }
    }
}
