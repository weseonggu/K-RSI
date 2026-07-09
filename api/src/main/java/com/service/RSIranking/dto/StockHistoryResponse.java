package com.service.RSIranking.dto;

import java.util.List;

/**
 * 종목 일자별 시계열 조회 API 응답 DTO.
 *
 * @param isuCd  종목 코드 (요청 원본)
 * @param market 시장 구분 (정규화된 대문자: "KOSPI" / "KOSDAQ")
 * @param items  일자별 시계열 항목 목록 (날짜 오름차순)
 *
 * @author RSIranking Team
 * @version 1.0
 */
public record StockHistoryResponse(
        String isuCd,
        String market,
        List<StockHistoryItemDto> items
) {
}
