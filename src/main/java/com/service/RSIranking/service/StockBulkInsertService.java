package com.service.RSIranking.service;

import com.service.RSIranking.entity.SecuritiesStockEntity;
import com.service.RSIranking.repository.jdbc.SecuritiesStockJDBCRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class StockBulkInsertService {
    private final SecuritiesStockJDBCRepository securitiesStockJDBCRepository;

    /**
     * 신규 종목 벌크 인서트
     * @param newStocks 신규 종목
     */
    @Transactional
    public void stocksInsert(List<SecuritiesStockEntity> newStocks){
        securitiesStockJDBCRepository.bulkInsert(newStocks);
    }
}
