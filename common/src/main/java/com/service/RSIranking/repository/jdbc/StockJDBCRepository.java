package com.service.RSIranking.repository.jdbc;


import com.service.RSIranking.entity.inter.StockInfoEntity;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

/**
 * 종목 정보 JDBC 리포지토리.
 *
 * <p>종목 정보의 대량 삽입을 위한 JDBC 기반 리포지토리입니다.
 * JPA의 성능 제한을 우회하여 효율적인 벌크 처리를 수행합니다.</p>
 *
 * @author RSIranking Team
 * @version 1.0
 */
@Repository
public class StockJDBCRepository {
    private final JdbcTemplate jdbcTemplate;

    public StockJDBCRepository(@Qualifier("jdbcDataTemplate") JdbcTemplate jdbcTemplate){
        this.jdbcTemplate = jdbcTemplate;

    }

    /**
     * KOSPI 종목 정보를 대량 삽입합니다.
     *
     * <p>이미 존재하는 종목은 건너뜁니다.</p>
     *
     * @param stocks 삽입할 종목 목록
     */
    public void kospiBulkInsert(List<StockInfoEntity> stocks) {

        String sql = """
                INSERT INTO kospi_stock_info (isu_cd, isu_nm, mkt_nm, is_public_stock)
                SELECT ?, ?, ?, ?
                WHERE NOT EXISTS (
                    SELECT 1 FROM kospi_stock_info s WHERE s.isu_cd = ?
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
    /**
     * KOSDAQ 종목 정보를 대량 삽입합니다.
     *
     * <p>이미 존재하는 종목은 건너뜁니다.</p>
     *
     * @param stocks 삽입할 종목 목록
     */
    public void kosdaqBulkInsert(List<StockInfoEntity> stocks) {

        String sql = """
                INSERT INTO kosdaq_stock_info (isu_cd, isu_nm, mkt_nm, is_public_stock)
                SELECT ?, ?, ?, ?
                WHERE NOT EXISTS (
                    SELECT 1 FROM kosdaq_stock_info s WHERE s.isu_cd = ?
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

    /** ETF 신규 종목을 대량 삽입합니다. */
    public void etfBulkInsert(List<StockInfoEntity> stocks) {
        String sql = """
                INSERT INTO etf_stock_info (isu_cd, isu_nm, mkt_nm, is_public_stock)
                SELECT ?, ?, ?, ?
                WHERE NOT EXISTS (
                    SELECT 1 FROM etf_stock_info s WHERE s.isu_cd = ?
                )
                """;
        jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                StockInfoEntity stock = stocks.get(i);
                ps.setString(1, stock.getId());
                ps.setString(2, stock.getIsuNm());
                ps.setString(3, stock.getMktNm());
                ps.setBoolean(4, stock.getIsPublicStock());
                ps.setString(5, stock.getId());
            }
            @Override public int getBatchSize() { return stocks.size(); }
        });
    }

}
