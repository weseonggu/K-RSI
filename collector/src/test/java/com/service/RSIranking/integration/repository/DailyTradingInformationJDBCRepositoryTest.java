package com.service.RSIranking.integration.repository;

import com.service.RSIranking.dto.RSIRankingDto;
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
 * <p>TDD 계획서: {@code _workflow/tdd/2026-07-05_rsi-trading-halt-volume-detection.md} 5.2절 T17~T18.</p>
 *
 * <p>순수 단위 테스트로 검증 불가능한 두 가지를 실제 SQL 실행으로 검증한다.</p>
 * <ul>
 *   <li>T17: {@code findRsiRanking} 의 {@code acc_trdvol > 0} 필터 (정지 종목이 rsi non-null 이어도 제외).</li>
 *   <li>T18: {@code findByIsuCdAndDateIn} 의 {@code rsi} nullable 매핑 (SQL NULL → 0.0 이 아닌 null).</li>
 * </ul>
 *
 * <p>비즈니스 테이블은 {@code JPADataDBConfig} 의 {@code hibernate.hbm2ddl.auto=update} 로
 * 테스트 컨테이너에 자동 생성된다(계획서 8장 2차 검토 권장 2). 데이터는
 * {@code RealDataCollectionTest} 의 TRUNCATE 패턴을 참고해 각 테스트 전에 초기화하고
 * {@link JdbcTemplate} 으로 직접 INSERT 한다.</p>
 *
 * <p><b>Red 단계 예상:</b> 현재 구현에서 T17 은 {@code acc_trdvol > 0} 필터가 없어 정지 종목이
 * 포함되어 실패하고, T18 은 매퍼가 {@code rs.getDouble("rsi")} 라 NULL 을 0.0 으로 읽어 실패한다.</p>
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

    // ================================ T17 ================================

    /**
     * T17 - [사용자 결정] 정지 종목(당일 accTrdvol=0)이 rsi non-null 이어도 랭킹에서 제외.
     *
     * <p>계획서 8장 2차 검토 권장 1 반영: (date, isu_cd) 유니크 제약이 있으므로 "동일 종목 2건"이
     * 아니라 <b>같은 날짜에 서로 다른 두 종목</b>을 넣는다.
     * A(정상, accTrdvol&gt;0, rsi=50.0) / B(정지, accTrdvol=0, rsi=50.0 복사됨).</p>
     *
     * <p>기대: {@code findRsiRanking} 결과에 A 만 포함되고 정지 종목 B 는 제외됨.</p>
     */
    @Test
    @DisplayName("T17 findRsiRanking - 정지 종목(accTrdvol=0)은 rsi non-null 이어도 제외")
    void t17_findRsiRanking_excludesSuspended() {
        insertStock("A00001", "정상종목");
        insertStock("B00002", "정지종목");
        insertTrading("A00001", D, 10_000L, 50.0); // 정상 거래
        insertTrading("B00002", D, 0L, 50.0);       // 정지(거래량 0)이나 rsi 복사됨

        List<RSIRankingDto> ranking = repository.findRsiRanking(D, MKT, true, 10);

        assertThat(ranking).extracting(RSIRankingDto::isuCd).contains("A00001");
        assertThat(ranking).extracting(RSIRankingDto::isuCd).doesNotContain("B00002");
    }

    // ================================ T18 ================================

    /**
     * T18 - [리뷰어 권장 5] rsi 컬럼 NULL 이 0.0 이 아닌 실제 null 로 매핑되는지 검증.
     *
     * <p>기대: {@code findByIsuCdAndDateIn} 결과 행의 {@code getRsi()} 가 {@code null}.
     * 현재 매퍼는 {@code rs.getDouble("rsi")} 라 0.0 을 반환 → 이 테스트는 Red 로 작성됨.</p>
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
}
