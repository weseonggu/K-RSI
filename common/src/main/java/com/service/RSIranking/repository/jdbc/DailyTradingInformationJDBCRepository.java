package com.service.RSIranking.repository.jdbc;

import com.service.RSIranking.dto.RSIRankingDto;
import com.service.RSIranking.dto.StockHistoryItemDto;
import com.service.RSIranking.dto.TradingInfoDto;
import com.service.RSIranking.entity.KospiDailyTradingInformation;
import com.service.RSIranking.entity.inter.DailyTradingInformation;
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
     * 일별 매매 정보를 대량 삽입합니다. (KOSPI/KOSDAQ 공용)
     *
     * <p>종목 정보 테이블에 존재하는 종목에 대해서만 삽입을 수행하며,
     * (isu_cd, date) 유니크 키에 걸리는 행은 {@code ON DUPLICATE KEY UPDATE id = id}
     * no-op으로 건너뛰어 재실행에 대해 멱등합니다.
     * (과거에는 중복 시 배치 전체가 예외로 실패하고 해당 날짜 전체를 DELETE하는
     * 롤백이 돌아, 이미 적재된 날짜를 재실행하면 기존 데이터가 삭제되는 문제가 있었음)</p>
     *
     * @param newTradingInfo 삽입할 매매 정보 엔티티 목록
     * @param baseInfoDtos 원본 DTO 목록 (종목 코드 참조용)
     * @param mktNm 시장 구분 ("KOSPI" / "KOSDAQ")
     * @throws Exception 데이터베이스 삽입 중 오류 발생 시
     */
    public void bulkInsert(List<? extends DailyTradingInformation> newTradingInfo,
                           List<TradingInfoDto> baseInfoDtos, String mktNm) throws Exception {
        String sql = String.format(
                """
                INSERT INTO %s
                (date, tdd_clsprc, cmpprevdd_prc, fluc_rt, tdd_opnprc, tdd_hgprc, tdd_lwprc, acc_trdvol, acc_trdval, isu_cd)
                SELECT ?, ?, ?, ?, ?, ?, ?, ?, ?, ? WHERE EXISTS
                ( SELECT isu_cd FROM %s s WHERE s.isu_cd = ?)
                ON DUPLICATE KEY UPDATE id = id
                """, resolveTableName(mktNm), resolveStockInfoTableName(mktNm));

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
                    .rsi(rs.getObject("rsi", Double.class))
                    .accTrdvol(rs.getLong("acc_trdvol"))
                    .accTrdval(rs.getLong("acc_trdval"))
                    .avgClosingGain(rs.getObject("avg_closing_gain", Double.class))
                    .avgClosingLoss(rs.getObject("avg_closing_loss", Double.class))
                    .build();
        });
    }

    /**
     * 특정 종목의 기준일 이하 최근 N 거래일 시계열(OHLCV+등락률+RSI)을 조회합니다.
     *
     * <p>종목 상세 페이지의 캔들/RSI 차트용 조회 메서드입니다. {@code date <= anchorDate}
     * 조건으로 기준일 당일을 포함(경계 포함)하며, 최근 {@code days}건을 날짜 내림차순으로
     * 잘라낸 뒤 바깥에서 날짜 오름차순으로 반전해 반환합니다.</p>
     *
     * <p>{@link #findRsiRanking}과 달리 {@code rsi IS NOT NULL} / {@code acc_trdvol > 0}
     * 필터를 걸지 않습니다(계획서 4.2절). 상장 초기 RSI 미계산 구간과 거래정지일도
     * 개별 종목 시계열에서는 캔들로 표시되어야 하는 정보이기 때문입니다.</p>
     *
     * @param isuCd      종목 코드
     * @param mktNm      시장 구분 ("KOSPI" / "KOSDAQ")
     * @param anchorDate 기준일 (이 날짜 이하만 조회, 경계 포함)
     * @param days       최근 거래일 행 개수 상한
     * @return 종목 시계열 목록 (날짜 오름차순, rsi/flucRt는 null 가능)
     */
    public List<StockHistoryItemDto> findHistory(String isuCd, String mktNm, LocalDate anchorDate, int days) {
        String tableName = resolveTableName(mktNm);

        String sql = String.format("""
        SELECT * FROM (
            SELECT date, tdd_opnprc, tdd_hgprc, tdd_lwprc, tdd_clsprc, acc_trdvol, fluc_rt, rsi
            FROM %s
            WHERE isu_cd = ? AND date <= ?
            ORDER BY date DESC
            LIMIT ?
        ) recent
        ORDER BY date ASC
        """, tableName);

        return jdbcTemplate.query(sql, (rs, rowNum) -> new StockHistoryItemDto(
                rs.getDate("date").toLocalDate(),
                rs.getInt("tdd_opnprc"),
                rs.getInt("tdd_hgprc"),
                rs.getInt("tdd_lwprc"),
                rs.getInt("tdd_clsprc"),
                rs.getLong("acc_trdvol"),
                rs.getObject("fluc_rt", Double.class),
                rs.getObject("rsi", Double.class)
        ), isuCd, anchorDate, days);
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
     * 특정 날짜의 RSI 순위를 조회합니다. (RSI 계산 완료된 종목만)
     *
     * <p>종목 정보 테이블과 조인하여 종목명을 함께 반환하며,
     * RSI 기준 오름/내림차순으로 정렬합니다. {@code rsiMin}/{@code rsiMax}가 지정되면
     * 경계값을 포함(&gt;=, &lt;=)하는 RSI 구간 필터가 함께 적용됩니다.</p>
     *
     * <p>{@code offset}은 페이지 경계를 넘어도 예외 없이 빈 결과를 반환하며(MySQL {@code LIMIT/OFFSET}
     * 표준 동작), 반환되는 {@code rank}는 {@code offset + rowNum + 1}로 계산되어 페이지 간 연속됩니다.</p>
     *
     * @param date   조회 날짜
     * @param mktNm  시장 구분 ("KOSPI" / "KOSDAQ")
     * @param asc    true면 RSI 오름차순(과매도 순), false면 내림차순(과매수 순)
     * @param rsiMin RSI 하한 (nullable, 지정 시 경계 포함)
     * @param rsiMax RSI 상한 (nullable, 지정 시 경계 포함)
     * @param offset 조회 시작 위치 (0-base)
     * @param size   페이지 크기
     * @return RSI 순위 목록
     */
    public List<RSIRankingDto> findRsiRanking(LocalDate date, String mktNm, boolean asc,
                                              Double rsiMin, Double rsiMax, long offset, int size) {
        String tableName = resolveTableName(mktNm);
        String stockTableName = resolveStockInfoTableName(mktNm);
        String direction = asc ? "ASC" : "DESC";

        List<Object> params = new ArrayList<>();
        params.add(date);
        String rangeClause = buildRsiRangeClause(rsiMin, rsiMax, params);

        String sql = String.format("""
        SELECT t.isu_cd, s.isu_nm, t.date, t.tdd_clsprc, t.fluc_rt, t.rsi
        FROM %s t
        JOIN %s s ON s.isu_cd = t.isu_cd
        WHERE t.date = ? AND t.rsi IS NOT NULL AND t.acc_trdvol > 0 %s
        ORDER BY t.rsi %s, t.isu_cd
        LIMIT ? OFFSET ?
        """, tableName, stockTableName, rangeClause, direction);

        params.add(size);
        params.add(offset);

        return jdbcTemplate.query(sql, (rs, rowNum) -> new RSIRankingDto(
                (int) (offset + rowNum + 1),
                rs.getString("isu_cd"),
                rs.getString("isu_nm"),
                rs.getDate("date").toLocalDate(),
                rs.getInt("tdd_clsprc"),
                rs.getDouble("fluc_rt"),
                rs.getObject("rsi", Double.class)
        ), params.toArray());
    }

    /**
     * 필터 조건(RSI 구간)에 해당하는 전체 건수를 조회합니다.
     *
     * <p>{@link #findRsiRanking}과 동일한 {@link #buildRsiRangeClause} 조건을 공유하여
     * 두 쿼리 사이의 필터 불일치를 방지합니다.</p>
     *
     * @param date   조회 날짜
     * @param mktNm  시장 구분 ("KOSPI" / "KOSDAQ")
     * @param rsiMin RSI 하한 (nullable)
     * @param rsiMax RSI 상한 (nullable)
     * @return 전체 건수
     */
    public long countRsiRanking(LocalDate date, String mktNm, Double rsiMin, Double rsiMax) {
        String tableName = resolveTableName(mktNm);
        List<Object> params = new ArrayList<>();
        params.add(date);
        String rangeClause = buildRsiRangeClause(rsiMin, rsiMax, params);

        String sql = String.format("""
        SELECT COUNT(*)
        FROM %s t
        WHERE t.date = ? AND t.rsi IS NOT NULL AND t.acc_trdvol > 0 %s
        """, tableName, rangeClause);

        Long count = jdbcTemplate.queryForObject(sql, Long.class, params.toArray());
        return count == null ? 0L : count;
    }

    /**
     * rsiMin/rsiMax가 지정된 만큼만 조건을 덧붙이고, params에 해당 바인드 값을 함께 추가합니다.
     * 경계값은 포함({@code >=}, {@code <=})합니다.
     */
    private static String buildRsiRangeClause(Double rsiMin, Double rsiMax, List<Object> params) {
        StringBuilder sb = new StringBuilder();
        if (rsiMin != null) {
            sb.append(" AND t.rsi >= ?");
            params.add(rsiMin);
        }
        if (rsiMax != null) {
            sb.append(" AND t.rsi <= ?");
            params.add(rsiMax);
        }
        return sb.toString();
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
            case "ETF" -> "etf_daily_trading_information";
            default -> throw new IllegalArgumentException("Unsupported mktNm: " + mktNm);
        };
    }

    /**
     * 시장 구분 문자열을 검증된 종목 정보 테이블 이름으로 변환합니다.
     */
    private static String resolveStockInfoTableName(String mktNm) {
        if (mktNm == null) {
            throw new IllegalArgumentException("mktNm must not be null");
        }
        return switch (mktNm.trim().toUpperCase()) {
            case "KOSPI" -> "kospi_stock_info";
            case "KOSDAQ" -> "kosdaq_stock_info";
            case "ETF" -> "etf_stock_info";
            default -> throw new IllegalArgumentException("Unsupported mktNm: " + mktNm);
        };
    }

    /**
     * 일별 매매 정보 삽입을 롤백합니다. (KOSPI/KOSDAQ 공용)
     *
     * <p>특정 날짜에 삽입된 모든 매매 정보를 삭제합니다.
     * 삽입이 멱등(ON DUPLICATE KEY no-op)해진 이후에는 진짜 삽입 오류일 때만 호출되어야 합니다.</p>
     *
     * @param date 롤백할 날짜
     * @param mktNm 시장 구분 ("KOSPI" / "KOSDAQ")
     * @throws RuntimeException 롤백 실패 시
     */
    public void insertRollback(LocalDate date, String mktNm) {
        String sql = String.format("DELETE FROM %s WHERE date = ?", resolveTableName(mktNm));
        try {
            jdbcTemplate.update(sql, date);
        } catch (Exception e) {
            throw new RuntimeException("롤백 실패", e);
        }
    }

}
