package com.service.RSIranking.repository.jdbc;

import com.service.RSIranking.dto.TradingInfoDto;
import com.service.RSIranking.entity.DailyTradingInformation;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

@Repository
public class DailyTradingInformationJDBCRepository {
    private final JdbcTemplate jdbcTemplate;

    public DailyTradingInformationJDBCRepository(@Qualifier("jdbcDataTemplate") JdbcTemplate jdbcTemplate){
        this.jdbcTemplate = jdbcTemplate;

    }

    public void bulkInsert(List<DailyTradingInformation> newTradingInfo, List<TradingInfoDto> baseInfoDtos) throws Exception {
        String sql =
                """
                INSERT INTO daily_trading_information
                (date, tdd_clsprc, cmpprevdd_prc, fluc_rt, tdd_opnprc, tdd_hgprc, tdd_lwprc, acc_trdvol, acc_trdval, isu_cd)
                SELECT ?, ?, ?, ?, ?, ?, ?, ?, ?, ? WHERE EXISTS
                ( SELECT isu_cd FROM stock_info s WHERE s.isu_cd = ?)
                """;

        jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                DailyTradingInformation stock = newTradingInfo.get(i);
                TradingInfoDto dto = baseInfoDtos.get(i);

                ps.setDate(1, Date.valueOf(stock.getDate()));
                ps.setInt(2, stock.getTddClsprc());
                ps.setInt(3, stock.getCmpprevddPrc());
                ps.setDouble(4, stock.getFlucRt());
                ps.setInt(5, stock.getTddOpnprc());
                ps.setInt(6, stock.getTddHgprc());
                ps.setInt(7, stock.getTddLwprc());
                ps.setLong(8, stock.getAccTrdvol());
                ps.setLong(9, stock.getAccTrdval());
                ps.setString(10, dto.getIsuCd());
                ps.setString(11, dto.getIsuCd());
            }

            @Override
            public int getBatchSize() {
                return newTradingInfo.size();
            }
        });
    }

}
