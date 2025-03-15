package com.service.RSIranking.batch.step;

import com.service.RSIranking.entity.SecuritiesStockEntity;
import com.service.RSIranking.repository.jpa.SecuritiesStockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;

@RequiredArgsConstructor
public class StockWriter implements ItemWriter<SecuritiesStockEntity> {

    private final SecuritiesStockRepository securitiesStockRepository;


    @Override
    public void write(Chunk<? extends SecuritiesStockEntity> chunk) throws Exception {

//        System.out.println("Writer 실행! 저장할 데이터: " + chunk);
        try {
            securitiesStockRepository.saveAll(chunk);  // 실제 DB 저장
//            System.out.println("Writer 저장 완료!");
        } catch (Exception e) {
//            System.err.println("Writer에서 예외 발생: " + e.getMessage());
            throw e;
        }
    }

}
