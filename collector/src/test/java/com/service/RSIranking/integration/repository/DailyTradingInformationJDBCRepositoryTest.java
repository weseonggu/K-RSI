package com.service.RSIranking.integration.repository;

import com.service.RSIranking.dto.RSIRankingDto;
import com.service.RSIranking.dto.StockHistoryItemDto;
import com.service.RSIranking.entity.KospiDailyTradingInformation;
import com.service.RSIranking.integration.AbstractIntegrationTest;
import com.service.RSIranking.repository.jdbc.DailyTradingInformationJDBCRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link DailyTradingInformationJDBCRepository} 통합 테스트 (Testcontainers MySQL).
 *
 * <p>TDD 계획서: {@code _workflow/tdd/2026-07-08_rsi-ranking-api-paging-filter.md} 5.2절 T-R1~T-R10.
 * 기존 {@code 2026-07-05} 계획서 산출물(T17→T-R8, T18)도 회귀로 유지한다.</p>
 *
 * <p>순수 단위 테스트로 검증 불가능한 실제 SQL 동작을 컨테이너로 검증한다.</p>
 * <ul>
 *   <li>T-R1/T-R2: RSI 구간 필터(범위 내/외, 경계값 포함 {@code >=}/{@code <=}).</li>
 *   <li>T-R3: 필터 없음(null/null) 회귀 - 거래정지만 제외.</li>
 *   <li>T-R4/T-R5: {@code LIMIT ? OFFSET ?} 페이징 + 페이지 경계를 넘는 rank 연속성.</li>
 *   <li>T-R6: {@code countRsiRanking} 필터 적용/미적용 COUNT 정확성.</li>
 *   <li>T-R7: 결측일(데이터 없음).</li>
 *   <li>T-R8: (기존 T17) 정지 종목 제외 회귀, 신규 시그니처로 갱신.</li>
 *   <li>T-R9: desc 방향 + OFFSET 페이징.</li>
 *   <li>T-R10: 초과 offset → 예외 없이 빈 결과.</li>
 *   <li>T18: {@code findByIsuCdAndDateIn} 의 rsi nullable 매핑 회귀.</li>
 * </ul>
 */
class DailyTradingInformationJDBCRepositoryTest extends AbstractIntegrationTest {

    private static final String MKT = "KOSPI";
    private static final LocalDate D = LocalDate.of(2026, 6, 3);

    @Autowired
    private DailyTradingInformationJDBCRepository repository;

    @Autowired
    @Qualifier("dataDBSource")
    private DataSource dataDataSource;

    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate = new JdbcTemplate(dataDataSource);
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 0");
        jdbcTemplate.execute("TRUNCATE TABLE kospi_daily_trading_information");
        jdbcTemplate.execute("TRUNCATE TABLE kospi_stock_info");
        jdbcTemplate.execute("TRUNCATE TABLE kosdaq_daily_trading_information");
        jdbcTemplate.execute("TRUNCATE TABLE kosdaq_stock_info");
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 1");
    }

    private void insertStock(String isuCd, String isuNm) {
        jdbcTemplate.update(
                "INSERT INTO kospi_stock_info (isu_cd, isu_nm, mkt_nm, is_public_stock) VALUES (?, ?, ?, ?)",
                isuCd, isuNm, MKT, true);
    }

    /** avg_closing_gain/loss/rsi 를 명시적으로 넘긴다 (rsi 는 null 가능). */
    private void insertTrading(String isuCd, LocalDate date, long accTrdvol, Double rsi) {
        jdbcTemplate.update("""
                INSERT INTO kospi_daily_trading_information
                (date, tdd_clsprc, cmpprevdd_prc, fluc_rt, tdd_opnprc, tdd_hgprc, tdd_lwprc,
                 rsi, acc_trdvol, acc_trdval, avg_closing_gain, avg_closing_loss, isu_cd)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                date, 1000, 0, 0.0, 1000, 1000, 1000,
                rsi, accTrdvol, 1_000_000L, 1.0, 1.0, isuCd);
    }

    /**
     * OHLC/거래량/rsi 를 날짜별로 서로 다른 값으로 명시 삽입한다 (findHistory 캔들 검증용, 계획서 5.3절).
     * fluc_rt/cmpprevdd_prc/acc_trdval/avg_* 는 검증 대상이 아니므로 상수로 둔다. rsi 는 null 가능.
     */
    private void insertTradingOhlc(String isuCd, LocalDate date, int open, int high, int low, int close,
                                   long accTrdvol, Double rsi) {
        jdbcTemplate.update("""
                INSERT INTO kospi_daily_trading_information
                (date, tdd_clsprc, cmpprevdd_prc, fluc_rt, tdd_opnprc, tdd_hgprc, tdd_lwprc,
                 rsi, acc_trdvol, acc_trdval, avg_closing_gain, avg_closing_loss, isu_cd)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                date, close, 0, 0.0, open, high, low,
                rsi, accTrdvol, 1_000_000L, 1.0, 1.0, isuCd);
    }

    /** 종목과 매매정보를 한 번에 삽입(정상 거래량). */
    private void insertStockAndTrading(String isuCd, double rsi) {
        insertStock(isuCd, "종목" + isuCd);
        insertTrading(isuCd, D, 10_000L, rsi);
    }

    /** rsi 5개(10/20/30/40/50)를 서로 다른 종목으로 삽입 - 정렬 결정적. */
    private void insertFiveDistinct() {
        insertStockAndTrading("S00010", 10.0);
        insertStockAndTrading("S00020", 20.0);
        insertStockAndTrading("S00030", 30.0);
        insertStockAndTrading("S00040", 40.0);
        insertStockAndTrading("S00050", 50.0);
    }

    // ================================ T-R1 ================================

    @Test
    @DisplayName("T-R1 findRsiRanking - RSI 구간 필터로 범위 내 종목만 반환")
    void tR1_rsiRangeFilter() {
        insertStockAndTrading("A00005", 5.0);
        insertStockAndTrading("A00015", 15.0);
        insertStockAndTrading("A00025", 25.0);

        List<RSIRankingDto> result = repository.findRsiRanking(D, MKT, true, 10.0, 20.0, 0L, 10);

        assertThat(result).extracting(RSIRankingDto::isuCd).containsExactly("A00015");
    }

    // ================================ T-R2 ================================

    @Test
    @DisplayName("T-R2 findRsiRanking - 경계값 포함(>=, <=)")
    void tR2_boundaryInclusive() {
        insertStockAndTrading("A00010", 10.0); // 하한과 동일
        insertStockAndTrading("A00020", 20.0); // 상한과 동일

        List<RSIRankingDto> result = repository.findRsiRanking(D, MKT, true, 10.0, 20.0, 0L, 10);

        assertThat(result).extracting(RSIRankingDto::isuCd).containsExactlyInAnyOrder("A00010", "A00020");
    }

    // ================================ T-R3 ================================

    @Test
    @DisplayName("T-R3 findRsiRanking - 필터 없음(null/null) 회귀: 거래정지만 제외")
    void tR3_noFilterExcludesHalted() {
        insertStockAndTrading("A00010", 10.0);
        insertStockAndTrading("A00030", 30.0);
        insertStock("H00099", "정지종목");
        insertTrading("H00099", D, 0L, 50.0); // 거래정지(acc_trdvol=0)

        List<RSIRankingDto> result = repository.findRsiRanking(D, MKT, true, null, null, 0L, 10);

        assertThat(result).extracting(RSIRankingDto::isuCd)
                .containsExactlyInAnyOrder("A00010", "A00030")
                .doesNotContain("H00099");
    }

    // ================================ T-R4 ================================

    @Test
    @DisplayName("T-R4 findRsiRanking - OFFSET 페이징(asc, size=2, offset=2 → 3~4번째)")
    void tR4_offsetPaging() {
        insertFiveDistinct();

        List<RSIRankingDto> result = repository.findRsiRanking(D, MKT, true, null, null, 2L, 2);

        // asc 정렬: S00010,S00020,S00030,S00040,S00050 → offset=2,size=2 → S00030,S00040
        assertThat(result).extracting(RSIRankingDto::isuCd).containsExactly("S00030", "S00040");
    }

    // ================================ T-R5 ================================

    @Test
    @DisplayName("T-R5 findRsiRanking - 페이지 경계 넘는 rank 연속성(1페이지 1,2 / 2페이지 3,4)")
    void tR5_rankContinuity() {
        insertFiveDistinct();

        List<RSIRankingDto> page1 = repository.findRsiRanking(D, MKT, true, null, null, 0L, 2);
        List<RSIRankingDto> page2 = repository.findRsiRanking(D, MKT, true, null, null, 2L, 2);

        assertThat(page1).extracting(RSIRankingDto::rank).containsExactly(1, 2);
        assertThat(page2).extracting(RSIRankingDto::rank).containsExactly(3, 4);
    }

    // ================================ T-R6 ================================

    @Test
    @DisplayName("T-R6 countRsiRanking - 필터 적용 시 1건, 미적용 시 (정지 제외) 전체")
    void tR6_count() {
        insertStockAndTrading("A00005", 5.0);
        insertStockAndTrading("A00015", 15.0);
        insertStockAndTrading("A00025", 25.0);

        long filtered = repository.countRsiRanking(D, MKT, 10.0, 20.0);
        long all = repository.countRsiRanking(D, MKT, null, null);

        assertThat(filtered).isEqualTo(1L);
        assertThat(all).isEqualTo(3L);
    }

    // ================================ T-R7 ================================

    @Test
    @DisplayName("T-R7 결측일 - findRsiRanking 빈 리스트, countRsiRanking 0")
    void tR7_missingDate() {
        List<RSIRankingDto> result = repository.findRsiRanking(D, MKT, true, null, null, 0L, 10);
        long count = repository.countRsiRanking(D, MKT, null, null);

        assertThat(result).isEmpty();
        assertThat(count).isEqualTo(0L);
    }

    // ================================ T-R8 (기존 T17) ================================

    @Test
    @DisplayName("T-R8 findRsiRanking - 정지 종목(accTrdvol=0)은 rsi non-null 이어도 제외(신규 시그니처)")
    void tR8_excludesSuspended() {
        insertStock("A00001", "정상종목");
        insertStock("B00002", "정지종목");
        insertTrading("A00001", D, 10_000L, 50.0); // 정상 거래
        insertTrading("B00002", D, 0L, 50.0);       // 정지(거래량 0)이나 rsi 복사됨

        List<RSIRankingDto> ranking = repository.findRsiRanking(D, MKT, true, null, null, 0L, 10);

        assertThat(ranking).extracting(RSIRankingDto::isuCd).contains("A00001");
        assertThat(ranking).extracting(RSIRankingDto::isuCd).doesNotContain("B00002");
    }

    // ================================ T-R9 ================================

    @Test
    @DisplayName("T-R9 findRsiRanking - desc 방향 + OFFSET 페이징(size=2, offset=2 → 3~4번째, rank 3,4)")
    void tR9_descOffsetPaging() {
        insertFiveDistinct();

        List<RSIRankingDto> result = repository.findRsiRanking(D, MKT, false, null, null, 2L, 2);

        // desc 정렬: S00050,S00040,S00030,S00020,S00010 → offset=2,size=2 → S00030,S00020
        assertThat(result).extracting(RSIRankingDto::isuCd).containsExactly("S00030", "S00020");
        assertThat(result).extracting(RSIRankingDto::rank).containsExactly(3, 4);
    }

    // ================================ T-R10 ================================

    @Test
    @DisplayName("T-R10 findRsiRanking - 초과 offset은 예외 없이 빈 결과, countRsiRanking 은 그대로")
    void tR10_overRangeOffset() {
        insertStockAndTrading("A00010", 10.0);
        insertStockAndTrading("A00020", 20.0);
        insertStockAndTrading("A00030", 30.0);

        List<RSIRankingDto> result = repository.findRsiRanking(D, MKT, true, null, null, 50L, 10);
        long count = repository.countRsiRanking(D, MKT, null, null);

        assertThat(result).isEmpty();
        assertThat(count).isEqualTo(3L);
    }

    // ================================ T18 (회귀 유지) ================================

    /**
     * T18 - rsi 컬럼 NULL 이 0.0 이 아닌 실제 null 로 매핑되는지 검증.
     */
    @Test
    @DisplayName("T18 findByIsuCdAndDateIn - rsi NULL 이 0.0 이 아닌 null 로 매핑")
    void t18_findByIsuCdAndDateIn_nullRsiMapping() {
        insertStock("A00001", "정상종목");
        insertTrading("A00001", D, 10_000L, null); // rsi = SQL NULL

        List<KospiDailyTradingInformation> rows =
                repository.findByIsuCdAndDateIn("A00001", List.of(D), MKT);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getRsi()).isNull();
    }

    // ============================================================================
    // findHistory (2026-07-09 계획서 5.2절 T-R1~T-R7 + 8장 T-R8/T-R9)
    // 위 T-R1~T-R10 은 findRsiRanking 대상이므로, 혼동을 피하기 위해 메서드명에 findHistory 접두어를 둔다.
    // ============================================================================

    private static final LocalDate HB = LocalDate.of(2026, 6, 1); // findHistory 기준일 베이스

    /** HB 로부터 i일 뒤 날짜에 결정적 OHLC(1000+i*10 계열)를 삽입. */
    private void insertHistoryDay(String isuCd, int i, long accTrdvol, Double rsi) {
        insertTradingOhlc(isuCd, HB.plusDays(i),
                1000 + i * 10, 1100 + i * 10, 900 + i * 10, 1050 + i * 10,
                accTrdvol, rsi);
    }

    // ================================ findHistory T-R1 ================================

    @Test
    @DisplayName("[findHistory] T-R1 기본 조회 - 최근 N건 오름차순 반환, OHLC 값 일치")
    void findHistory_tR1_recentAscending() {
        insertStock("A00001", "종목A");
        for (int i = 0; i < 10; i++) {
            insertHistoryDay("A00001", i, 10_000L + i, 50.0 + i);
        }

        List<StockHistoryItemDto> result = repository.findHistory("A00001", MKT, HB.plusDays(9), 5);

        // 최근 5건(i=5..9)이 날짜 오름차순으로 반환
        assertThat(result).hasSize(5);
        assertThat(result).extracting(StockHistoryItemDto::date)
                .containsExactly(HB.plusDays(5), HB.plusDays(6), HB.plusDays(7), HB.plusDays(8), HB.plusDays(9));

        StockHistoryItemDto last = result.get(4); // i=9
        assertThat(last.openPrice()).isEqualTo(1090);
        assertThat(last.highPrice()).isEqualTo(1190);
        assertThat(last.lowPrice()).isEqualTo(990);
        assertThat(last.closePrice()).isEqualTo(1140);
        assertThat(last.volume()).isEqualTo(10_009L);
        assertThat(last.rsi()).isEqualTo(59.0);
    }

    // ================================ findHistory T-R2 ================================

    @Test
    @DisplayName("[findHistory] T-R2 days > 실제 거래일 수 → 존재하는 만큼만 오름차순(예외 아님)")
    void findHistory_tR2_daysExceedsAvailable() {
        insertStock("A00001", "종목A");
        for (int i = 0; i < 3; i++) {
            insertHistoryDay("A00001", i, 10_000L, 50.0);
        }

        List<StockHistoryItemDto> result = repository.findHistory("A00001", MKT, HB.plusDays(2), 10);

        assertThat(result).hasSize(3);
        assertThat(result).extracting(StockHistoryItemDto::date)
                .containsExactly(HB, HB.plusDays(1), HB.plusDays(2));
    }

    // ================================ findHistory T-R3 ================================

    @Test
    @DisplayName("[findHistory] T-R3 anchor 이후(미래) 데이터는 제외")
    void findHistory_tR3_excludesFuture() {
        insertStock("A00001", "종목A");
        for (int i = 0; i < 5; i++) {
            insertHistoryDay("A00001", i, 10_000L, 50.0);
        }

        // anchor = HB+2 → HB+3, HB+4 는 미래이므로 제외
        List<StockHistoryItemDto> result = repository.findHistory("A00001", MKT, HB.plusDays(2), 10);

        assertThat(result).extracting(StockHistoryItemDto::date)
                .containsExactly(HB, HB.plusDays(1), HB.plusDays(2))
                .doesNotContain(HB.plusDays(3), HB.plusDays(4));
    }

    // ================================ findHistory T-R4 ================================

    @Test
    @DisplayName("[findHistory] T-R4 존재하지 않는 isuCd → 빈 리스트")
    void findHistory_tR4_unknownIsuCd() {
        insertStock("A00001", "종목A");
        insertHistoryDay("A00001", 0, 10_000L, 50.0);

        List<StockHistoryItemDto> result = repository.findHistory("NOPE", MKT, HB.plusDays(9), 5);

        assertThat(result).isEmpty();
    }

    // ================================ findHistory T-R5 ================================

    @Test
    @DisplayName("[findHistory] T-R5 KOSPI 테이블에만 삽입 후 KOSDAQ 조회 → 빈 리스트(시장 테이블 분리)")
    void findHistory_tR5_marketTableIsolation() {
        insertStock("A00001", "종목A");
        insertHistoryDay("A00001", 0, 10_000L, 50.0);

        List<StockHistoryItemDto> result = repository.findHistory("A00001", "KOSDAQ", HB.plusDays(9), 5);

        assertThat(result).isEmpty();
    }

    // ================================ findHistory T-R6 ================================

    @Test
    @DisplayName("[findHistory] T-R6 rsi NULL 구간도 포함(rsi IS NOT NULL 필터 미적용, 4.2절)")
    void findHistory_tR6_includesNullRsi() {
        insertStock("A00001", "종목A");
        insertHistoryDay("A00001", 0, 10_000L, null); // rsi = SQL NULL
        insertHistoryDay("A00001", 1, 10_000L, 55.0);

        List<StockHistoryItemDto> result = repository.findHistory("A00001", MKT, HB.plusDays(1), 5);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).date()).isEqualTo(HB);
        assertThat(result.get(0).rsi()).isNull(); // 제외되지 않고 null 로 매핑
        assertThat(result.get(1).rsi()).isEqualTo(55.0);
    }

    // ================================ findHistory T-R7 ================================

    @Test
    @DisplayName("[findHistory] T-R7 거래정지(acc_trdvol=0) 일자도 포함(4.2절)")
    void findHistory_tR7_includesHaltedDay() {
        insertStock("A00001", "종목A");
        insertHistoryDay("A00001", 0, 0L, 50.0);       // 거래정지(거래량 0)
        insertHistoryDay("A00001", 1, 10_000L, 55.0);

        List<StockHistoryItemDto> result = repository.findHistory("A00001", MKT, HB.plusDays(1), 5);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).date()).isEqualTo(HB);
        assertThat(result.get(0).volume()).isEqualTo(0L); // 제외되지 않고 포함
    }

    // ================================ findHistory T-R8 (8장 추가) ================================

    @Test
    @DisplayName("[findHistory] T-R8 다종목 혼재 시 요청 isuCd 행만 반환(격리)")
    void findHistory_tR8_isolatesByIsuCd() {
        insertStock("A00001", "종목A");
        insertStock("B00002", "종목B");
        for (int i = 0; i < 5; i++) {
            insertHistoryDay("A00001", i, 10_000L, 50.0);
            insertTradingOhlc("B00002", HB.plusDays(i), 2000, 2100, 1900, 2050, 20_000L, 60.0);
        }

        List<StockHistoryItemDto> result = repository.findHistory("A00001", MKT, HB.plusDays(4), 10);

        assertThat(result).hasSize(5);
        // B 종목 특유의 시가(2000)가 결과에 섞이지 않음 → A(1000 계열)만 반환
        assertThat(result).extracting(StockHistoryItemDto::openPrice)
                .allMatch(op -> op >= 1000 && op < 1100);
    }

    // ================================ findHistory T-R9 (8장 추가) ================================

    @Test
    @DisplayName("[findHistory] T-R9 anchor 당일 포함 경계(date <= ? 의 = 포함)")
    void findHistory_tR9_anchorInclusive() {
        insertStock("A00001", "종목A");
        for (int i = 0; i < 5; i++) {
            insertHistoryDay("A00001", i, 10_000L, 50.0);
        }

        LocalDate anchor = HB.plusDays(4);
        List<StockHistoryItemDto> result = repository.findHistory("A00001", MKT, anchor, 10);

        assertThat(result).isNotEmpty();
        assertThat(result.get(result.size() - 1).date()).isEqualTo(anchor); // 마지막 원소가 anchor 당일
    }
}
