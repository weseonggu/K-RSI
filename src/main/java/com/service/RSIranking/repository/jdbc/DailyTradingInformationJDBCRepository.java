package com.service.RSIranking.repository.jdbc;

import com.service.RSIranking.dto.TradingInfoDto;
import com.service.RSIranking.entity.KosdaqDailyTradingInformation;
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

/**
 * 일별 매매 정보 JDBC 리포지토리.
 *
 * <p>일별 매매 정보의 대량 삽입 및 조회를 위한 JDBC 기반 리포지토리입니다.
 * JPA의 성능 제한을 우회하여 효율적인 벌크 처리를 수행합니다.</p>
 *
 * <p>주요 기능:</p>
 * <ul>
 *   <li>KOSPI/KOSDAQ 일별 매매 정보 벌크 삽입</li>
 *   <li>특정 종목의 14일간 매매 정보 조회 (RSI 계산용)</li>
 *   <li>삽입 실패 시 롤백 처리</li>
 * </ul>
 *
 * @author RSIranking Team
 * @version 1.0
 * @see com.service.RSIranking.entity.KospiDailyTradingInformation
 * @see com.service.RSIranking.entity.KosdaqDailyTradingInformation
 */
@Repository
public class DailyTradingInformationJDBCRepository {
    private final JdbcTemplate jdbcTemplate;

    public DailyTradingInformationJDBCRepository(@Qualifier("jdbcDataTemplate") JdbcTemplate jdbcTemplate){
        this.jdbcTemplate = jdbcTemplate;

    }

    /**
     * KOSPI 일별 매매 정보를 대량 삽입합니다.
     *
     * <p>종목 정보 테이블(kospi_stock_info)에 존재하는 종목에 대해서만 삽입을 수행합니다.
     * EXISTS 서브쿼리를 사용하여 데이터 무결성을 보장합니다.</p>
     *
     * @param newTradingInfo 삽입할 매매 정보 엔티티 목록
     * @param baseInfoDtos 원본 DTO 목록 (종목 코드 참조용)
     * @throws Exception 데이터베이스 삽입 중 오류 발생 시
     */
    public void kospiBulkInsert(List<KospiDailyTradingInformation> newTradingInfo, List<TradingInfoDto> baseInfoDtos) throws Exception {
        String sql =
                """
                INSERT INTO kospi_daily_trading_information
                (date, tdd_clsprc, cmpprevdd_prc, fluc_rt, tdd_opnprc, tdd_hgprc, tdd_lwprc, acc_trdvol, acc_trdval, isu_cd)
                SELECT ?, ?, ?, ?, ?, ?, ?, ?, ?, ? WHERE EXISTS
                ( SELECT isu_cd FROM kospi_stock_info s WHERE s.isu_cd = ?)
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
     * KOSDAQ 일별 매매 정보를 대량 삽입합니다.
     *
     * <p>종목 정보 테이블(kosdaq_stock_info)에 존재하는 종목에 대해서만 삽입을 수행합니다.
     * EXISTS 서브쿼리를 사용하여 데이터 무결성을 보장합니다.</p>
     *
     * @param newTradingInfo 삽입할 매매 정보 엔티티 목록
     * @param baseInfoDtos 원본 DTO 목록 (종목 코드 참조용)
     * @throws Exception 데이터베이스 삽입 중 오류 발생 시
     */
    public void kosdaqBulkInsert(List<KosdaqDailyTradingInformation> newTradingInfo, List<TradingInfoDto> baseInfoDtos) throws Exception {
        String sql =
                """
                INSERT INTO kosdaq_daily_trading_information
                (date, tdd_clsprc, cmpprevdd_prc, fluc_rt, tdd_opnprc, tdd_hgprc, tdd_lwprc, acc_trdvol, acc_trdval, isu_cd)
                SELECT ?, ?, ?, ?, ?, ?, ?, ?, ?, ? WHERE EXISTS
                ( SELECT isu_cd FROM kosdaq_stock_info s WHERE s.isu_cd = ?)
                """;

        jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                KosdaqDailyTradingInformation stock = newTradingInfo.get(i);
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
     * 특정 종목의 지정된 날짜들에 대한 매매 정보를 조회합니다.
     *
     * <p>RSI 계산을 위해 최근 14일간의 매매 정보를 조회하는 데 사용됩니다.
     * 결과는 날짜 기준 내림차순으로 정렬됩니다.</p>
     *
     * <p>{@code mktNm}에 따라 {@code kospi_daily_trading_information} 또는
     * {@code kosdaq_daily_trading_information} 테이블을 조회합니다.</p>
     *
     * @param isuCd 종목 코드
     * @param dates 조회할 날짜 목록
     * @param mktNm 시장 구분 ("KOSPI" / "KOSDAQ")
     * @return 해당 종목의 매매 정보 목록 (날짜 내림차순)
     */
    public List<KospiDailyTradingInformation> findByIsuCdAndDateIn(String isuCd, List<LocalDate> dates, String mktNm) {
        if (dates == null || dates.isEmpty()) {
            return Collections.emptyList();
        }

        String tableName = resolveTableName(mktNm);

        String inSql = dates.stream()
                .map(d -> "?")
                .collect(Collectors.joining(", "));

        String sql = String.format("""
        SELECT
            id, date, tdd_clsprc, cmpprevdd_prc, fluc_rt, tdd_opnprc, tdd_hgprc, tdd_lwprc,
            rsi, acc_trdvol, acc_trdval, avg_closing_gain, avg_closing_loss
        FROM %s
        WHERE isu_cd = ? AND date IN (%s)
        ORDER BY date DESC
        """, tableName, inSql);

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

    /**
     * RSI 계산 결과를 시장에 맞는 테이블에 직접 UPDATE합니다.
     *
     * <p>JPA 경로(legacy {@code daily_trading_information} 단일 테이블 의존)를 우회하기 위한
     * 분리 테이블 직접 업데이트 메서드입니다.</p>
     *
     * @param isuCd 종목 코드
     * @param date  대상 날짜
     * @param ag    평균 상승폭
     * @param al    평균 하락폭
     * @param rsi   RSI 지표
     * @param mktNm 시장 구분 ("KOSPI" / "KOSDAQ")
     * @return 업데이트된 행 수 (정상이면 1)
     */
    public int updateRsi(String isuCd, LocalDate date, Double ag, Double al, Double rsi, String mktNm) {
        String tableName = resolveTableName(mktNm);
        String sql = String.format("""
        UPDATE %s
        SET avg_closing_gain = ?, avg_closing_loss = ?, rsi = ?
        WHERE isu_cd = ? AND date = ?
        """, tableName);
        return jdbcTemplate.update(sql, ag, al, rsi, isuCd, date);
    }

    /**
     * 시장 구분 문자열을 검증된 테이블 이름으로 변환합니다.
     * SQL 인젝션 방지를 위해 화이트리스트만 허용합니다.
     */
    private static String resolveTableName(String mktNm) {
        if (mktNm == null) {
            throw new IllegalArgumentException("mktNm must not be null");
        }
        return switch (mktNm.trim().toUpperCase()) {
            case "KOSPI" -> "kospi_daily_trading_information";
            case "KOSDAQ" -> "kosdaq_daily_trading_information";
            default -> throw new IllegalArgumentException("Unsupported mktNm: " + mktNm);
        };
    }

    /**
     * KOSPI 일별 매매 정보 삽입을 롤백합니다.
     *
     * <p>특정 날짜에 삽입된 모든 KOSPI 매매 정보를 삭제합니다.
     * 배치 처리 중 오류 발생 시 데이터 일관성을 유지하기 위해 사용됩니다.</p>
     *
     * @param date 롤백할 날짜
     * @throws RuntimeException 롤백 실패 시
     */
    public void kospiInsertRollback(LocalDate date) {
        String sql = "DELETE FROM kospi_daily_trading_information WHERE date = ?";
        try {
            int deletedCount = jdbcTemplate.update(sql, date);
        } catch (Exception e) {
            throw new RuntimeException("롤백 실패", e);
        }
    }

    /**
     * KOSDAQ 일별 매매 정보 삽입을 롤백합니다.
     *
     * <p>특정 날짜에 삽입된 모든 KOSDAQ 매매 정보를 삭제합니다.
     * 배치 처리 중 오류 발생 시 데이터 일관성을 유지하기 위해 사용됩니다.</p>
     *
     * @param date 롤백할 날짜
     * @throws RuntimeException 롤백 실패 시
     */
    public void kosdaqInsertRollback(LocalDate date) {
        String sql = "DELETE FROM kosdaq_daily_trading_information WHERE date = ?";
        try {
            int deletedCount = jdbcTemplate.update(sql, date);
        } catch (Exception e) {
            throw new RuntimeException("롤백 실패", e);
        }
    }

}
