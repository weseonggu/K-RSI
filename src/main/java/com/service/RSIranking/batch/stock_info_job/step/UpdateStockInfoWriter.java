package com.service.RSIranking.batch.stock_info_job.step;


import com.service.RSIranking.entity.inter.StockInfoEntity;
import com.service.RSIranking.repository.jpa.KosdaqStockRepository;
import com.service.RSIranking.repository.jpa.KospiStockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 종목 정보 업데이트 Writer.
 *
 * <p>Processor에서 변경이 감지된 종목 정보를 데이터베이스에 업데이트합니다.
 * KOSPI/KOSDAQ 시장 구분에 따라 각각의 리포지토리를 사용합니다.</p>
 *
 * <h2>업데이트 항목</h2>
 * <ul>
 *   <li>종목명 (isuNm)</li>
 *   <li>시장 구분 (mktNm)</li>
 *   <li>상장 여부 (isPublicStock)</li>
 * </ul>
 *
 * @author RSIranking Team
 * @version 1.0
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UpdateStockInfoWriter implements ItemWriter<StockInfoEntity> {

    private final KospiStockRepository kospiStockRepository;
    private final KosdaqStockRepository kosdaqStockRepository;

    /**
     * 변경된 종목 정보를 데이터베이스에 업데이트합니다.
     *
     * @param chunk 업데이트할 종목 정보 청크
     * @throws Exception 업데이트 중 예외 발생 시
     */
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
                // 코스피, 코스닥 분기 처리
                if(entity.getMktNm().equals("KOSPI")){
                    kospiStockRepository.updateStockInfoByCode(
                            entity.getId(),
                            entity.getIsuNm(),
                            entity.getMktNm(),
                            entity.getIsPublicStock()
                    );
                }else{
                    kosdaqStockRepository.updateStockInfoByCode(
                            entity.getId(),
                            entity.getIsuNm(),
                            entity.getMktNm(),
                            entity.getIsPublicStock()
                    );
                }
            } // 실제 DB 저장
            log.info("DB업데이트 끝");
        } catch (Exception e) {
            throw e;
        }
    }
}
