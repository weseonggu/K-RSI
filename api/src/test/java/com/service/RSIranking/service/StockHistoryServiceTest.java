package com.service.RSIranking.service;

import com.service.RSIranking.dto.StockHistoryItemDto;
import com.service.RSIranking.dto.StockHistoryResponse;
import com.service.RSIranking.repository.jdbc.DailyTradingInformationJDBCRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link StockHistoryService} 순수 단위 테스트 (Mockito).
 *
 * <p>TDD 계획서: {@code _workflow/tdd/2026-07-09_stock-detail-hts-chart-mobile-fix.md} 5.2절 T-S1~T-S10 + 8장 T-S11.</p>
 *
 * <p>{@link DailyTradingInformationJDBCRepository} 를 mock 으로 대체해 Spring 컨텍스트/DB 없이
 * 파라미터 검증(isuCd/market/date/days)과 기본값(days=null→120) 적용, 리포지토리 위임 파라미터,
 * 응답 DTO 조립/매핑을 검증한다.</p>
 *
 * <p>리뷰어 필수 수정 2: days 기본값 책임은 서비스 소유. days=null → 120 으로 {@code findHistory(...,120)} 위임.</p>
 */
@ExtendWith(MockitoExtension.class)
class StockHistoryServiceTest {

    private static final String ISU = "A00001";
    private static final String MARKET = "KOSPI";
    private static final String DATE = "20260709";
    private static final LocalDate ANCHOR = LocalDate.of(2026, 7, 9);

    @Mock
    private DailyTradingInformationJDBCRepository repository;

    @InjectMocks
    private StockHistoryService service;

    @BeforeEach
    void setUp() {
        // 검증을 통과해 리포지토리까지 도달하는 정상 케이스의 기본 스텁 (미사용 시 무해하도록 lenient).
        lenient().when(repository.findHistory(any(), any(), any(), anyInt()))
                .thenReturn(List.of());
    }

    // ================================ T-S1 ================================

    @Test
    @DisplayName("T-S1 정상 요청 → 정규화된 파라미터로 리포지토리 위임")
    void tS1_normalDelegation() {
        service.getHistory(ISU, MARKET, DATE, 60);

        verify(repository).findHistory(eq("A00001"), eq("KOSPI"), eq(ANCHOR), eq(60));
    }

    // ================================ T-S2 ================================

    @Test
    @DisplayName("T-S2 market 소문자(kospi) → 대문자 정규화 후 위임")
    void tS2_marketNormalized() {
        service.getHistory(ISU, "kospi", DATE, 60);

        verify(repository).findHistory(eq("A00001"), eq("KOSPI"), eq(ANCHOR), eq(60));
    }

    // ================================ T-S3 ================================

    @Test
    @DisplayName("T-S3 market 미지원 값(NYSE) → IllegalArgumentException")
    void tS3_unsupportedMarket() {
        assertThatThrownBy(() -> service.getHistory(ISU, "NYSE", DATE, 60))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ================================ T-S4 ================================

    @Test
    @DisplayName("T-S4 date 형식 오류(2026-07-09) → IllegalArgumentException")
    void tS4_invalidDateFormat() {
        assertThatThrownBy(() -> service.getHistory(ISU, MARKET, "2026-07-09", 60))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ================================ T-S5 ================================

    @Test
    @DisplayName("T-S5 date 누락(null) → IllegalArgumentException")
    void tS5_nullDate() {
        assertThatThrownBy(() -> service.getHistory(ISU, MARKET, null, 60))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ================================ T-S6 ================================

    @Test
    @DisplayName("T-S6 isuCd 누락(blank) → IllegalArgumentException")
    void tS6_blankIsuCd() {
        assertThatThrownBy(() -> service.getHistory("", MARKET, DATE, 60))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ================================ T-S7 ================================

    @Test
    @DisplayName("T-S7 days 생략(null) → 기본값 120 적용해 findHistory(...,120) 위임 (기본값 책임=서비스)")
    void tS7_daysNullDefaultsTo120() {
        service.getHistory(ISU, MARKET, DATE, null);

        verify(repository).findHistory(eq("A00001"), eq("KOSPI"), eq(ANCHOR), eq(120));
    }

    // ================================ T-S8 ================================

    @Test
    @DisplayName("T-S8 days 범위 밖(0, 1501) → 각각 IllegalArgumentException")
    void tS8_daysOutOfRange() {
        assertThatThrownBy(() -> service.getHistory(ISU, MARKET, DATE, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.getHistory(ISU, MARKET, DATE, 1501))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ================================ T-S9 ================================

    @Test
    @DisplayName("T-S9 리포지토리 빈 결과 → 예외 아닌 정상 응답(items 빈 리스트)")
    void tS9_emptyResult() {
        when(repository.findHistory(any(), any(), any(), anyInt())).thenReturn(List.of());

        StockHistoryResponse response = service.getHistory(ISU, MARKET, DATE, 60);

        assertThat(response.items()).isEmpty();
        assertThat(response.isuCd()).isEqualTo("A00001");
        assertThat(response.market()).isEqualTo("KOSPI");
    }

    // ================================ T-S10 ================================

    @Test
    @DisplayName("T-S10 리포지토리 결과 → 응답 DTO 필드 매핑(rsi=null 유지)")
    void tS10_fieldMapping() {
        StockHistoryItemDto item = new StockHistoryItemDto(
                ANCHOR, 1000, 1100, 900, 1050, 12345L, 1.5, null);
        when(repository.findHistory(any(), any(), any(), anyInt())).thenReturn(List.of(item));

        StockHistoryResponse response = service.getHistory(ISU, MARKET, DATE, 60);

        assertThat(response.isuCd()).isEqualTo("A00001");
        assertThat(response.market()).isEqualTo("KOSPI");
        assertThat(response.items()).hasSize(1);
        StockHistoryItemDto mapped = response.items().get(0);
        assertThat(mapped.date()).isEqualTo(ANCHOR);
        assertThat(mapped.openPrice()).isEqualTo(1000);
        assertThat(mapped.highPrice()).isEqualTo(1100);
        assertThat(mapped.lowPrice()).isEqualTo(900);
        assertThat(mapped.closePrice()).isEqualTo(1050);
        assertThat(mapped.volume()).isEqualTo(12345L);
        assertThat(mapped.flucRt()).isEqualTo(1.5);
        assertThat(mapped.rsi()).isNull();
    }

    // ================================ T-S11 (8장 추가) ================================

    @Test
    @DisplayName("T-S11 days 경계값(1, 1500) 정상 통과 → findHistory(...,1)/(...,1500) 위임")
    void tS11_daysBoundaryValues() {
        service.getHistory(ISU, MARKET, DATE, 1);
        verify(repository).findHistory(eq("A00001"), eq("KOSPI"), eq(ANCHOR), eq(1));

        service.getHistory(ISU, MARKET, DATE, 1500);
        verify(repository).findHistory(eq("A00001"), eq("KOSPI"), eq(ANCHOR), eq(1500));
    }
}
