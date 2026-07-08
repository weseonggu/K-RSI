package com.service.RSIranking.controller;

import com.service.RSIranking.dto.PagedResponse;
import com.service.RSIranking.dto.RSIRankingDto;
import com.service.RSIranking.service.RSIRankingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link RSIRankingController} 얇은 MockMvc 테스트 (@WebMvcTest).
 *
 * <p>TDD 계획서: {@code _workflow/tdd/2026-07-08_rsi-ranking-api-paging-filter.md} 5.2절 T-C1~T-C5 (4.6절, 필수).</p>
 *
 * <p>서비스는 {@link MockBean} 으로 대체하며, 조회 결과 정확성이 아니라 쿼리 파라미터 바인딩(기본값/필수여부)과
 * 에러 응답 형태(400 + {@code {"error": ...}})만 검증한다. 특히 T-C5 는 {@code rsiMin}/{@code rsiMax} 미지정 시
 * 400 이 아니라 서비스에 {@code null} 이 전달되고 200 이 반환되는지(=required=false 바인딩)를 고정한다.</p>
 */
@WebMvcTest(RSIRankingController.class)
class RSIRankingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RSIRankingService rsiRankingService;

    private PagedResponse<RSIRankingDto> samplePage() {
        return new PagedResponse<>(
                List.of(new RSIRankingDto(1, "A00001", "종목1", LocalDate.of(2026, 7, 1), 1000, 0.0, 10.0)),
                0, 50, 1L, 1);
    }

    // ================================ T-C1 ================================

    @Test
    @DisplayName("T-C1 order 파라미터 생략 시 서비스에 \"asc\" 로 전달")
    void tC1_orderDefaultsToAsc() throws Exception {
        when(rsiRankingService.getRanking(any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(samplePage());

        mockMvc.perform(get("/api/rsi/ranking")
                        .param("date", "20260701")
                        .param("market", "KOSPI"))
                .andExpect(status().isOk());

        verify(rsiRankingService).getRanking(eq("20260701"), eq("KOSPI"), eq("asc"),
                isNull(), isNull(), eq(0), eq(50));
    }

    // ================================ T-C2 ================================

    @Test
    @DisplayName("T-C2 rsiMin/rsiMax/page/size 쿼리 파라미터가 정확히 바인딩됨")
    void tC2_paramBinding() throws Exception {
        when(rsiRankingService.getRanking(any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(samplePage());

        mockMvc.perform(get("/api/rsi/ranking")
                        .param("date", "20260701")
                        .param("market", "KOSPI")
                        .param("rsiMin", "10")
                        .param("rsiMax", "20")
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk());

        verify(rsiRankingService).getRanking(eq("20260701"), eq("KOSPI"), eq("asc"),
                eq(10.0), eq(20.0), eq(1), eq(10));
    }

    // ================================ T-C3 ================================

    @Test
    @DisplayName("T-C3 서비스 IllegalArgumentException → 400 + {\"error\": ...}")
    void tC3_badRequest() throws Exception {
        when(rsiRankingService.getRanking(any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenThrow(new IllegalArgumentException("잘못된 파라미터"));

        mockMvc.perform(get("/api/rsi/ranking")
                        .param("date", "20260701")
                        .param("market", "KOSPI"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("잘못된 파라미터"));
    }

    // ================================ T-C4 ================================

    @Test
    @DisplayName("T-C4 정상 응답 JSON 에 items/page/size/totalElements/totalPages 필드 존재")
    void tC4_responseJsonStructure() throws Exception {
        when(rsiRankingService.getRanking(any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(samplePage());

        mockMvc.perform(get("/api/rsi/ranking")
                        .param("date", "20260701")
                        .param("market", "KOSPI"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").exists())
                .andExpect(jsonPath("$.page").exists())
                .andExpect(jsonPath("$.size").exists())
                .andExpect(jsonPath("$.totalElements").exists())
                .andExpect(jsonPath("$.totalPages").exists());
    }

    // ================================ T-C5 ================================

    @Test
    @DisplayName("T-C5 rsiMin/rsiMax 미지정 시 400 아니라 200 + 서비스에 null 전달(required=false)")
    void tC5_optionalRsiParamsBindNull() throws Exception {
        when(rsiRankingService.getRanking(any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(samplePage());

        mockMvc.perform(get("/api/rsi/ranking")
                        .param("date", "20260701")
                        .param("market", "KOSPI"))
                .andExpect(status().isOk());

        ArgumentCaptor<Double> minCaptor = ArgumentCaptor.forClass(Double.class);
        ArgumentCaptor<Double> maxCaptor = ArgumentCaptor.forClass(Double.class);
        verify(rsiRankingService).getRanking(eq("20260701"), eq("KOSPI"), any(),
                minCaptor.capture(), maxCaptor.capture(), anyInt(), anyInt());
        org.assertj.core.api.Assertions.assertThat(minCaptor.getValue()).isNull();
        org.assertj.core.api.Assertions.assertThat(maxCaptor.getValue()).isNull();
    }
}
