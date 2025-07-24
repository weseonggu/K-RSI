package com.service.RSIranking.batch.stock_info_job.step;


import com.service.RSIranking.entity.inter.StockInfoEntity;
import com.service.RSIranking.repository.jpa.SecuritiesStockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class UpdateStockInfoWriter implements ItemWriter<StockInfoEntity> {

    private final SecuritiesStockRepository securitiesStockRepository;

    @Override
    @Transactional("dataTransactionManager")// 트랜잭션 매니저 빈 직접 지정해줘야함
    public void write(Chunk<? extends StockInfoEntity> chunk) throws Exception {
        try {
            log.info("DB업데이트 시작");
            for (StockInfoEntity entity : chunk) {
                if(entity == null){
                    log.info(entity.getId() + "변경사항 없음 계속");
                    continue;
                }
                // 이 repository는 위에서 정의한 update JPQL 메서드를 호출
                log.info(entity.getId() + "변경사항 저장");
                securitiesStockRepository.updateStockInfoByCode(
                        entity.getId(),
                        entity.getIsuNm(),
                        entity.getMktNm(),
                        entity.getIsPublicStock()
                );
            } // 실제 DB 저장
            log.info("DB업데이트 끝");
        } catch (Exception e) {
            throw e;
        }
    }
}
