package com.service.RSIranking.service;

import com.service.RSIranking.dto.PagedResponse;
import com.service.RSIranking.dto.RSIRankingDto;
import com.service.RSIranking.repository.jdbc.DailyTradingInformationJDBCRepository;
import com.service.RSIranking.repository.jdbc.StockJDBCRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link RSIRankingService} 순수 단위 테스트 (Mockito).
 *
 * <p>TDD 계획서: {@code _workflow/tdd/2026-07-08_rsi-ranking-api-paging-filter.md} 5.2절 T-S1~T-S20.</p>
 *
 * <p>{@link DailyTradingInformationJDBCRepository} 를 mock 으로 대체해 Spring 컨텍스트/DB 없이
 * 파라미터 검증, long offset 계산/오버플로 방지, 초과 페이지 계약, PagedResponse 조립을 검증한다.</p>
 */
@ExtendWith(MockitoExtension.class)
class RSIRankingServiceTest {

    private static final String DATE = "20260701";
    private static final LocalDate TARGET_DATE = LocalDate.of(2026, 7, 1);
    private static final String MARKET = "KOSPI";

    @Mock
    private DailyTradingInformationJDBCRepository repository;

    @Mock
    private StockJDBCRepository stockRepository;

    @InjectMocks
    private RSIRankingService service;

    @Test
    @DisplayName("선택 종목 식별코드를 순위 조회와 개수 조회에 전달한다")
    void selectedStockFiltersRanking() {
        service.getRanking(DATE, MARKET, "asc", null, null, "005930", 0, 50);

        verify(repository).findRsiRanking(TARGET_DATE, MARKET, true, null, null, "005930", 0L, 50);
        verify(repository).countRsiRanking(TARGET_DATE, MARKET, null, null, "005930");
    }

    @Test
    @DisplayName("검색 자동완성은 최대 5개를 요청한다")
    void searchSuggestionsAreLimitedToFive() {
        service.searchStocks(MARKET, "삼성");

        verify(stockRepository).search("삼성", MARKET, 5);
    }

    @Test
    @DisplayName("최신 RSI 거래일을 시장별로 조회한다")
    void latestDateUsesSelectedMarket() {
        when(repository.findLatestRsiDate("ETF")).thenReturn(TARGET_DATE);

        assertThat(service.getLatestDate("etf")).isEqualTo(TARGET_DATE);
    }

    @BeforeEach
    void setUp() {
        // 검증을 통과해 리포지토리까지 도달하는 정상 케이스의 기본 스텁 (미사용 시 무해하도록 lenient).
        lenient().when(repository.findRsiRanking(any(), any(), anyBoolean(), any(), any(), anyLong(), anyInt()))
                .thenReturn(List.of());
        lenient().when(repository.countRsiRanking(any(), any(), any(), any()))
                .thenReturn(0L);
    }

    private RSIRankingDto dto(int rank) {
        return new RSIRankingDto(rank, "A0000" + rank, "종목" + rank, TARGET_DATE, 1000, 0.0, (double) rank);
    }

    // ================================ T-S1~T-S4: order 파싱 ================================

    @Test
    @DisplayName("T-S1 order 미지정(null) → asc=true 로 리포지토리 위임")
    void tS1_orderNull_defaultsToAsc() {
        service.getRanking(DATE, MARKET, null, null, null, 0, 50);

        verify(repository).findRsiRanking(eq(TARGET_DATE), eq(MARKET), eq(true), isNull(), isNull(), eq(0L), eq(50));
    }

    @Test
    @DisplayName("T-S2 order=desc → asc=false 로 리포지토리 위임")
    void tS2_orderDesc() {
        service.getRanking(DATE, MARKET, "desc", null, null, 0, 50);

        verify(repository).findRsiRanking(eq(TARGET_DATE), eq(MARKET), eq(false), isNull(), isNull(), eq(0L), eq(50));
    }

    @Test
    @DisplayName("T-S3 order=asc → asc=true 로 리포지토리 위임")
    void tS3_orderAsc() {
        service.getRanking(DATE, MARKET, "asc", null, null, 0, 50);

        verify(repository).findRsiRanking(eq(TARGET_DATE), eq(MARKET), eq(true), isNull(), isNull(), eq(0L), eq(50));
    }

    @Test
    @DisplayName("T-S4 order 잘못된 값 → IllegalArgumentException")
    void tS4_orderInvalid() {
        assertThatThrownBy(() -> service.getRanking(DATE, MARKET, "invalid", null, null, 0, 50))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ================================ T-S5~T-S11: rsiMin/rsiMax 검증 ================================

    @Test
    @DisplayName("T-S5 rsiMin/rsiMax 모두 null → 필터 없이 그대로 위임")
    void tS5_bothNull() {
        service.getRanking(DATE, MARKET, "asc", null, null, 0, 50);

        verify(repository).findRsiRanking(eq(TARGET_DATE), eq(MARKET), eq(true), isNull(), isNull(), eq(0L), eq(50));
    }

    @Test
    @DisplayName("T-S6 rsiMin=10.0, rsiMax=20.0 → 그대로 위임")
    void tS6_validRange() {
        service.getRanking(DATE, MARKET, "asc", 10.0, 20.0, 0, 50);

        verify(repository).findRsiRanking(eq(TARGET_DATE), eq(MARKET), eq(true), eq(10.0), eq(20.0), eq(0L), eq(50));
    }

    @Test
    @DisplayName("T-S7 rsiMin 만 지정(단측 필터) → 정상 위임")
    void tS7_onlyMin() {
        service.getRanking(DATE, MARKET, "asc", 90.0, null, 0, 50);

        verify(repository).findRsiRanking(eq(TARGET_DATE), eq(MARKET), eq(true), eq(90.0), isNull(), eq(0L), eq(50));
    }

    @Test
    @DisplayName("T-S8 rsiMax 만 지정(단측 필터) → 정상 위임")
    void tS8_onlyMax() {
        service.getRanking(DATE, MARKET, "asc", null, 10.0, 0, 50);

        verify(repository).findRsiRanking(eq(TARGET_DATE), eq(MARKET), eq(true), isNull(), eq(10.0), eq(0L), eq(50));
    }

    @Test
    @DisplayName("T-S9 rsiMin > rsiMax → IllegalArgumentException")
    void tS9_minGreaterThanMax() {
        assertThatThrownBy(() -> service.getRanking(DATE, MARKET, "asc", 50.0, 10.0, 0, 50))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("T-S10 rsiMin/rsiMax 범위 밖(0~100) → 각각 IllegalArgumentException")
    void tS10_outOfRange() {
        assertThatThrownBy(() -> service.getRanking(DATE, MARKET, "asc", -1.0, null, 0, 50))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.getRanking(DATE, MARKET, "asc", null, 101.0, 0, 50))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("T-S11 rsiMin == rsiMax(경계 동일값) → 정상 위임(에러 아님)")
    void tS11_minEqualsMax() {
        service.getRanking(DATE, MARKET, "asc", 10.0, 10.0, 0, 50);

        verify(repository).findRsiRanking(eq(TARGET_DATE), eq(MARKET), eq(true), eq(10.0), eq(10.0), eq(0L), eq(50));
    }

    // ================================ T-S12~T-S13: page/size 검증 ================================

    @Test
    @DisplayName("T-S12 page 음수 → IllegalArgumentException")
    void tS12_negativePage() {
        assertThatThrownBy(() -> service.getRanking(DATE, MARKET, "asc", null, null, -1, 50))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("T-S13 size 범위 밖(1~500) → 각각 IllegalArgumentException")
    void tS13_sizeOutOfRange() {
        assertThatThrownBy(() -> service.getRanking(DATE, MARKET, "asc", null, null, 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.getRanking(DATE, MARKET, "asc", null, null, 0, 501))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ================================ T-S14: offset 계산 ================================

    @Test
    @DisplayName("T-S14 page=2, size=20 → offset=40 으로 리포지토리 위임")
    void tS14_offsetCalculation() {
        service.getRanking(DATE, MARKET, "asc", null, null, 2, 20);

        verify(repository).findRsiRanking(eq(TARGET_DATE), eq(MARKET), eq(true), isNull(), isNull(), eq(40L), eq(20));
    }

    // ================================ T-S15~T-S16, T-S20: PagedResponse 조립 ================================

    @Test
    @DisplayName("T-S15 PagedResponse 조립 - 정상(105건/size20 → totalPages=6)")
    void tS15_pagedResponseAssembly() {
        when(repository.findRsiRanking(any(), any(), anyBoolean(), any(), any(), anyLong(), anyInt()))
                .thenReturn(List.of(dto(1), dto(2)));
        when(repository.countRsiRanking(any(), any(), any(), any())).thenReturn(105L);

        PagedResponse<RSIRankingDto> result = service.getRanking(DATE, MARKET, "asc", null, null, 0, 20);

        assertThat(result.totalElements()).isEqualTo(105L);
        assertThat(result.totalPages()).isEqualTo(6); // ceil(105/20)
        assertThat(result.page()).isEqualTo(0);
        assertThat(result.size()).isEqualTo(20);
        assertThat(result.items()).hasSize(2);
    }

    @Test
    @DisplayName("T-S16 PagedResponse 조립 - 빈 결과(count=0 → totalPages=0)")
    void tS16_pagedResponseEmpty() {
        when(repository.findRsiRanking(any(), any(), anyBoolean(), any(), any(), anyLong(), anyInt()))
                .thenReturn(List.of());
        when(repository.countRsiRanking(any(), any(), any(), any())).thenReturn(0L);

        PagedResponse<RSIRankingDto> result = service.getRanking(DATE, MARKET, "asc", null, null, 0, 50);

        assertThat(result.items()).isEmpty();
        assertThat(result.totalElements()).isEqualTo(0L);
        assertThat(result.totalPages()).isEqualTo(0);
    }

    @Test
    @DisplayName("T-S20 totalPages 나머지 없는 배수 경계(100건/size20 → totalPages=5)")
    void tS20_totalPagesExactMultiple() {
        when(repository.countRsiRanking(any(), any(), any(), any())).thenReturn(100L);

        PagedResponse<RSIRankingDto> result = service.getRanking(DATE, MARKET, "asc", null, null, 0, 20);

        assertThat(result.totalPages()).isEqualTo(5); // ceil(100/20)=5, 초과 페이지 미생성
    }

    // ================================ T-S17: 기존 date/market 검증 회귀 ================================

    @Test
    @DisplayName("T-S17 기존 date/market 검증 회귀 - 잘못된 날짜/미지원 시장 → IllegalArgumentException")
    void tS17_dateMarketRegression() {
        assertThatThrownBy(() -> service.getRanking("2026-07-01", MARKET, "asc", null, null, 0, 50))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.getRanking(DATE, "NYSE", "asc", null, null, 0, 50))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("ETF 시장은 정규화 후 랭킹 리포지토리에 위임한다")
    void etfMarketIsSupported() {
        service.getRanking(DATE, "etf", "asc", null, null, 0, 50);
        verify(repository).findRsiRanking(eq(TARGET_DATE), eq("ETF"), eq(true), isNull(), isNull(), eq(0L), eq(50));
    }

    // ================================ T-S18: 초과 페이지 계약 ================================

    @Test
    @DisplayName("T-S18 초과 페이지 - 전체는 있으나 요청 페이지가 마지막 초과 시 예외 없이 빈 items 반환")
    void tS18_overRangePage() {
        when(repository.findRsiRanking(any(), any(), anyBoolean(), any(), any(), anyLong(), anyInt()))
                .thenReturn(List.of());
        when(repository.countRsiRanking(any(), any(), any(), any())).thenReturn(3L);

        PagedResponse<RSIRankingDto> result = service.getRanking(DATE, MARKET, "asc", null, null, 5, 10);

        assertThat(result.items()).isEmpty();
        assertThat(result.totalElements()).isEqualTo(3L);
        assertThat(result.totalPages()).isEqualTo(1); // ceil(3/10)=1
        assertThat(result.page()).isEqualTo(5);        // 입력 그대로 반영
    }

    // ================================ T-S19: offset 오버플로 경계 ================================

    @Test
    @DisplayName("T-S19 offset 오버플로 경계 - page=Integer.MAX_VALUE, size=500 → 양수 long offset")
    void tS19_offsetOverflowGuard() {
        service.getRanking(DATE, MARKET, "asc", null, null, Integer.MAX_VALUE, 500);

        ArgumentCaptor<Long> offsetCaptor = ArgumentCaptor.forClass(Long.class);
        verify(repository).findRsiRanking(eq(TARGET_DATE), eq(MARKET), eq(true), isNull(), isNull(),
                offsetCaptor.capture(), eq(500));

        long expected = (long) Integer.MAX_VALUE * 500; // ≈ 1.07×10^12
        assertThat(offsetCaptor.getValue()).isEqualTo(expected);
        assertThat(offsetCaptor.getValue()).isPositive(); // 오버플로로 인한 음수 미발생
    }
}
