package com.service.RSIranking.dto;

import java.time.LocalDate;

/**
 * 종목 일자별 시계열(OHLCV+등락률+RSI) 조회 응답 항목 DTO.
 *
 * <p>종목 상세 페이지의 캔들스틱/거래량/RSI 차트 및 일별 목록에 사용됩니다.
 * RSI는 상장 초기 14일 미만 구간에서 {@code null}일 수 있으며(배치 미계산),
 * 등락률({@code flucRt})도 nullable로 다룹니다.</p>
 *
 * @param date       기준 날짜
 * @param openPrice  시가
 * @param highPrice  고가
 * @param lowPrice   저가
 * @param closePrice 종가
 * @param volume     거래량 (acc_trdvol)
 * @param flucRt     등락률 (nullable)
 * @param rsi        RSI 지표 값 (nullable, 초기 14일 미만 구간에서 null)
 *
 * @author RSIranking Team
 * @version 1.0
 */
public record StockHistoryItemDto(
        LocalDate date,
        int openPrice,
        int highPrice,
        int lowPrice,
        int closePrice,
        long volume,
        Double flucRt,
        Double rsi
) {
}
