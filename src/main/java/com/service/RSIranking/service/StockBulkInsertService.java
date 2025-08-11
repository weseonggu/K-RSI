package com.service.RSIranking.service;


import com.service.RSIranking.entity.inter.StockInfoEntity;
import com.service.RSIranking.repository.jdbc.StockJDBCRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class StockBulkInsertService {
    private final StockJDBCRepository stockJDBCRepository;

    /**
     * 신규 종목 벌크 인서트
     * @param newStocks 신규 종목
     */
    @Transactional
    public void KospiStocksInsert(List<StockInfoEntity> newStocks){
        stockJDBCRepository.kospiBulkInsert(newStocks);
    }

    @Transactional
    public void KosdaqStocksInsert(List<StockInfoEntity> newStocks){
        stockJDBCRepository.kosdaqBulkInsert(newStocks);
    }
}
