package com.service.RSIranking.dto;

import java.time.LocalDate;

/**
 * RSI 순위 조회 응답 DTO.
 *
 * @param rank       순위 (1부터 시작)
 * @param isuCd      종목 코드
 * @param isuNm      종목명
 * @param date       기준 날짜
 * @param closePrice 종가
 * @param flucRt     등락률
 * @param rsi        RSI 지표 값
 *
 * @author RSIranking Team
 * @version 1.0
 */
public record RSIRankingDto(
        int rank,
        String isuCd,
        String isuNm,
        LocalDate date,
        int closePrice,
        double flucRt,
        Double rsi
) {
}
