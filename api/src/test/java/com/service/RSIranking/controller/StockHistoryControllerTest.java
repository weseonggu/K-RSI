package com.service.RSIranking.controller;

import com.service.RSIranking.dto.StockHistoryItemDto;
import com.service.RSIranking.dto.StockHistoryResponse;
import com.service.RSIranking.service.StockHistoryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link StockHistoryController} 얇은 MockMvc 테스트 (@WebMvcTest).
 *
 * <p>TDD 계획서: {@code _workflow/tdd/2026-07-09_stock-detail-hts-chart-mobile-fix.md} 5.2절 T-C1~T-C5 + 8장 T-C7.</p>
 *
 * <p>서비스는 {@link MockBean} 으로 대체하며, 조회 결과 정확성이 아니라 쿼리/패스 파라미터 바인딩과
 * 에러 응답 형태만 검증한다.</p>
 *
 * <p>예외 핸들러는 전역 {@code ApiExceptionHandler}({@code @RestControllerAdvice}) 경유로
 * 400 + {@code {"error": ...}} 를 낸다(T-C5). {@code @RestControllerAdvice} 빈은 {@code @WebMvcTest}
 * 슬라이스에 포함되므로 이 테스트에서도 그대로 적용된다.</p>
 * <p>리뷰어 필수 수정 2: days 기본값 책임=서비스. 컨트롤러는 {@code required=false Integer days} 로 받아
 * null 그대로 위임(T-C2 는 {@code isNull()} 검증).</p>
 */
@WebMvcTest(StockHistoryController.class)
class StockHistoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private StockHistoryService stockHistoryService;

    private StockHistoryResponse sampleResponse() {
        return new StockHistoryResponse("A00001", "KOSPI", List.of(
                new StockHistoryItemDto(LocalDate.of(2026, 7, 9), 1000, 1100, 900, 1050, 12345L, 1.5, 55.5)));
    }

    // ================================ T-C1 ================================

    @Test
    @DisplayName("T-C1 market 파라미터 누락 → 400")
    void tC1_marketMissing() throws Exception {
        mockMvc.perform(get("/api/rsi/stock/{isuCd}/history", "A00001")
                        .param("date", "20260709"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("T-C1 date 파라미터 누락 → 400")
    void tC1_dateMissing() throws Exception {
        mockMvc.perform(get("/api/rsi/stock/{isuCd}/history", "A00001")
                        .param("market", "KOSPI"))
                .andExpect(status().isBadRequest());
    }

    // ================================ T-C2 ================================

    @Test
    @DisplayName("T-C2 days 생략 → 서비스에 null 전달(기본값 책임=서비스)")
    void tC2_daysOmittedPassesNull() throws Exception {
        when(stockHistoryService.getHistory(any(), any(), any(), any()))
                .thenReturn(sampleResponse());

        mockMvc.perform(get("/api/rsi/stock/{isuCd}/history", "A00001")
                        .param("market", "KOSPI")
                        .param("date", "20260709"))
                .andExpect(status().isOk());

        verify(stockHistoryService).getHistory(eq("A00001"), eq("KOSPI"), eq("20260709"), isNull());
    }

    // ================================ T-C3 ================================

    @Test
    @DisplayName("T-C3 쿼리/패스 파라미터 정확히 바인딩")
    void tC3_paramBinding() throws Exception {
        when(stockHistoryService.getHistory(any(), any(), any(), any()))
                .thenReturn(sampleResponse());

        mockMvc.perform(get("/api/rsi/stock/{isuCd}/history", "A00001")
                        .param("market", "KOSPI")
                        .param("date", "20260709")
                        .param("days", "30"))
                .andExpect(status().isOk());

        verify(stockHistoryService).getHistory(eq("A00001"), eq("KOSPI"), eq("20260709"), eq(30));
    }

    // ================================ T-C4 ================================

    @Test
    @DisplayName("T-C4 정상 응답 JSON 구조($.isuCd/$.market/$.items[0].*)")
    void tC4_responseJsonStructure() throws Exception {
        when(stockHistoryService.getHistory(any(), any(), any(), any()))
                .thenReturn(sampleResponse());

        mockMvc.perform(get("/api/rsi/stock/{isuCd}/history", "A00001")
                        .param("market", "KOSPI")
                        .param("date", "20260709")
                        .param("days", "30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isuCd").exists())
                .andExpect(jsonPath("$.market").exists())
                .andExpect(jsonPath("$.items").exists())
                .andExpect(jsonPath("$.items[0].date").exists())
                .andExpect(jsonPath("$.items[0].openPrice").exists())
                .andExpect(jsonPath("$.items[0].highPrice").exists())
                .andExpect(jsonPath("$.items[0].lowPrice").exists())
                .andExpect(jsonPath("$.items[0].closePrice").exists())
                .andExpect(jsonPath("$.items[0].volume").exists())
                .andExpect(jsonPath("$.items[0].flucRt").exists())
                .andExpect(jsonPath("$.items[0].rsi").exists());
    }

    // ================================ T-C5 ================================

    @Test
    @DisplayName("T-C5 서비스 IllegalArgumentException → 400 + {\"error\": ...} (전역 핸들러)")
    void tC5_badRequest() throws Exception {
        when(stockHistoryService.getHistory(any(), any(), any(), any()))
                .thenThrow(new IllegalArgumentException("잘못된 파라미터"));

        mockMvc.perform(get("/api/rsi/stock/{isuCd}/history", "A00001")
                        .param("market", "KOSPI")
                        .param("date", "20260709"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("잘못된 파라미터"));
    }

    // ================================ T-C7 (8장 추가) ================================

    @Test
    @DisplayName("T-C7 days 타입 미스매치(abc) → 400 (응답 바디 형식 미검증)")
    void tC7_daysTypeMismatch() throws Exception {
        mockMvc.perform(get("/api/rsi/stock/{isuCd}/history", "A00001")
                        .param("market", "KOSPI")
                        .param("date", "20260709")
                        .param("days", "abc"))
                .andExpect(status().isBadRequest());
    }
}
