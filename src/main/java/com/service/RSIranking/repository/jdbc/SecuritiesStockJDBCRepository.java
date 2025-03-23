package com.service.RSIranking.repository.jdbc;

import com.service.RSIranking.entity.SecuritiesStockEntity;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class SecuritiesStockJDBCRepository {
    private final JdbcTemplate jdbcTemplate;

    public SecuritiesStockJDBCRepository(@Qualifier("jdbcDataTemplate") JdbcTemplate jdbcTemplate){
        this.jdbcTemplate = jdbcTemplate;

    }

    public void bulkInsert(List<SecuritiesStockEntity> stocks) {
        String sql = "INSERT INTO SecuritiesStockEntity (isu_cd, isu_nm, mkt_nm, is_public_stock) VALUES (?, ?, ?, ?)";

        List<Object[]> batchArgs = stocks.stream()
                .map(stock -> new Object[]{
                        stock.getId(),
                        stock.getIsuNm(),
                        stock.getMktNm(),
                        stock.getIsPublicStock()
                })
                .toList();
        try {
            jdbcTemplate.batchUpdate(sql, batchArgs);
        }catch (Exception e){
            throw new RuntimeException(e);
        }
    }

}
