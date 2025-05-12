package com.service.RSIranking.batch.step;

import com.service.RSIranking.entity.SecuritiesStockEntity;
import com.service.RSIranking.repository.jpa.SecuritiesStockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Slf4j
public class StockWriter implements ItemWriter<SecuritiesStockEntity> {

    private final SecuritiesStockRepository securitiesStockRepository;

    @Override
    @Transactional
    public void write(Chunk<? extends SecuritiesStockEntity> chunk) throws Exception {
        // todo 더티 채킹이 안되는 문제 밝생
        try {
            log.info("DB업데이트 시작");
//            securitiesStockRepository.saveAll(chunk);  // 실제 DB 저장
            for (SecuritiesStockEntity entity : chunk) {
                securitiesStockRepository.save(entity);
            }
            log.info("DB업데이트 끝");
        } catch (Exception e) {
            throw e;
        }
    }
}
