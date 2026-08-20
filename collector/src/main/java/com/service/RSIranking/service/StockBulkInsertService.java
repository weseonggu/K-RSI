package com.service.RSIranking.service;


import com.service.RSIranking.entity.inter.StockInfoEntity;
import com.service.RSIranking.repository.jdbc.StockJDBCRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 종목 정보 대량 삽입 서비스.
 *
 * <p>신규 상장 종목을 데이터베이스에 대량으로 삽입하는 서비스입니다.
 * JDBC 배치 처리를 통해 효율적인 대량 삽입을 수행합니다.</p>
 *
 * @author RSIranking Team
 * @version 1.0
 * @see StockJDBCRepository
 */
@Service
@RequiredArgsConstructor
public class StockBulkInsertService {
    private final StockJDBCRepository stockJDBCRepository;

    /**
     * KOSPI 신규 종목을 대량으로 삽입합니다.
     *
     * @param newStocks 삽입할 신규 종목 목록
     */
    @Transactional
    public void KospiStocksInsert(List<StockInfoEntity> newStocks){
        stockJDBCRepository.kospiBulkInsert(newStocks);
    }

    /**
     * KOSDAQ 신규 종목을 대량으로 삽입합니다.
     *
     * @param newStocks 삽입할 신규 종목 목록
     */
    @Transactional
    public void KosdaqStocksInsert(List<StockInfoEntity> newStocks){
        stockJDBCRepository.kosdaqBulkInsert(newStocks);
    }

    @Transactional
    public void EtfStocksInsert(List<StockInfoEntity> newStocks){
        stockJDBCRepository.etfBulkInsert(newStocks);
    }
}
