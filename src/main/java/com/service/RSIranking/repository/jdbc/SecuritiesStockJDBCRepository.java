package com.service.RSIranking.repository.jdbc;

import com.service.RSIranking.entity.StockInfoEntity;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

@Repository
public class SecuritiesStockJDBCRepository {
    private final JdbcTemplate jdbcTemplate;

    public SecuritiesStockJDBCRepository(@Qualifier("jdbcDataTemplate") JdbcTemplate jdbcTemplate){
        this.jdbcTemplate = jdbcTemplate;

    }

    public void bulkInsert(List<StockInfoEntity> stocks) {
        String sql = """
                INSERT INTO stock_info (isu_cd, isu_nm, mkt_nm, is_public_stock)
                SELECT ?, ?, ?, ?
                WHERE NOT EXISTS (
                    SELECT 1 FROM stock_info s WHERE s.isu_cd = ?
                )
                """;


        jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                StockInfoEntity stock = stocks.get(i);

                ps.setString(1, stock.getId());               // isu_cd
                ps.setString(2, stock.getIsuNm());            // isu_nm
                ps.setString(3, stock.getMktNm());            // mkt_nm
                ps.setBoolean(4, stock.getIsPublicStock());   // is_public_stock
                ps.setString(5, stock.getId());               // EXISTS 조건용 isu_cd
            }

            @Override
            public int getBatchSize() {
                return stocks.size();
            }
        });
    }

}
