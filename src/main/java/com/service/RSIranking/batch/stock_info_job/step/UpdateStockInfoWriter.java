package com.service.RSIranking.batch.stock_info_job.step;

import com.service.RSIranking.entity.StockInfoEntity;
import com.service.RSIranking.repository.jpa.SecuritiesStockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Slf4j
public class UpdateStockInfoWriter implements ItemWriter<StockInfoEntity> {

    private final SecuritiesStockRepository securitiesStockRepository;

    @Override
    @Transactional
    public void write(Chunk<? extends StockInfoEntity> chunk) throws Exception {
        try {
            log.info("DB업데이트 시작");
            securitiesStockRepository.saveAll(chunk);  // 실제 DB 저장
            log.info("DB업데이트 끝");
        } catch (Exception e) {
            throw e;
        }
    }
}
