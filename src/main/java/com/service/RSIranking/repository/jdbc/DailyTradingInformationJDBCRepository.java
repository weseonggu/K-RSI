package com.service.RSIranking.repository.jdbc;

import com.service.RSIranking.dto.TradingInfoDto;
import com.service.RSIranking.entity.KospiDailyTradingInformation;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Repository
public class DailyTradingInformationJDBCRepository {
    private final JdbcTemplate jdbcTemplate;

    public DailyTradingInformationJDBCRepository(@Qualifier("jdbcDataTemplate") JdbcTemplate jdbcTemplate){
        this.jdbcTemplate = jdbcTemplate;

    }

    /**
     * 일일 매매 정보 벌크 인서트 메소드
     * @param newTradingInfo
     * @param baseInfoDtos
     * @throws Exception
     */
    public void bulkInsert(List<KospiDailyTradingInformation> newTradingInfo, List<TradingInfoDto> baseInfoDtos) throws Exception {
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
                KospiDailyTradingInformation stock = newTradingInfo.get(i);
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

    /**
     * 14일 매매 거래 조회 메소드
     * @param isuCd
     * @param dates
     * @return
     */
    public List<KospiDailyTradingInformation> findByIsuCdAndDateIn(String isuCd, List<LocalDate> dates) {
        if (dates == null || dates.isEmpty()) {
            return Collections.emptyList();
        }

        String inSql = dates.stream()
                .map(d -> "?")
                .collect(Collectors.joining(", "));

        String sql = String.format("""
        SELECT 
            id, date, tdd_clsprc, cmpprevdd_prc, fluc_rt, tdd_opnprc, tdd_hgprc, tdd_lwprc, 
            rsi, acc_trdvol, acc_trdval, avg_closing_gain, avg_closing_loss
        FROM daily_trading_information
        WHERE isu_cd = ? AND date IN (%s)
        ORDER BY date DESC
        """, inSql);

        List<Object> params = new ArrayList<>();
        params.add(isuCd);
        params.addAll(dates);

        return jdbcTemplate.query(sql, params.toArray(), (rs, rowNum) -> {
            return KospiDailyTradingInformation.builder()
                    .id(rs.getLong("id"))
                    .date(rs.getDate("date").toLocalDate())
                    .tddClsprc(rs.getInt("tdd_clsprc"))
                    .cmpprevddPrc(rs.getInt("cmpprevdd_prc"))
                    .flucRt(rs.getDouble("fluc_rt"))
                    .tddOpnprc(rs.getInt("tdd_opnprc"))
                    .tddHgprc(rs.getInt("tdd_hgprc"))
                    .tddLwprc(rs.getInt("tdd_lwprc"))
                    .rsi(rs.getDouble("rsi"))
                    .accTrdvol(rs.getLong("acc_trdvol"))
                    .accTrdval(rs.getLong("acc_trdval"))
                    .avgClosingGain(rs.getObject("avg_closing_gain", Double.class))
                    .avgClosingLoss(rs.getObject("avg_closing_loss", Double.class))
                    .build();
        });
    }

    public void insertRollback(LocalDate date) {
        String sql = "DELETE FROM daily_trading_information WHERE date = ?";
        try {
            int deletedCount = jdbcTemplate.update(sql, date);
        } catch (Exception e) {
            throw new RuntimeException("롤백 실패", e);
        }
    }

}
