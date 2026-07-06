package com.service.RSIranking.service;

import com.service.RSIranking.dto.RSIRankingDto;
import com.service.RSIranking.repository.jdbc.DailyTradingInformationJDBCRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Set;

/**
 * RSI 순위 조회 서비스.
 *
 * <p>배치가 적재한 일별 RSI 지표를 순위 형태로 조회합니다.</p>
 *
 * @author RSIranking Team
 * @version 1.0
 */
@Service
@RequiredArgsConstructor
public class RSIRankingService {

    private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final Set<String> SUPPORTED_MARKETS = Set.of("KOSPI", "KOSDAQ");
    private static final int MAX_LIMIT = 500;

    private final DailyTradingInformationJDBCRepository dailyTradingInformationJDBCRepository;

    /**
     * 특정 날짜의 RSI 순위를 조회합니다.
     *
     * @param date   조회 날짜 (yyyyMMdd)
     * @param market 시장 구분 ("KOSPI" / "KOSDAQ", 대소문자 무관)
     * @param order  정렬 방향 ("desc": 과매수 순 / "asc": 과매도 순)
     * @param limit  최대 조회 건수 (1~500)
     * @return RSI 순위 목록 (RSI가 계산된 종목만)
     * @throws IllegalArgumentException 파라미터가 유효하지 않은 경우
     */
    public List<RSIRankingDto> getRanking(String date, String market, String order, int limit) {
        LocalDate targetDate = parseDate(date);
        String mktNm = normalizeMarket(market);
        boolean asc = parseOrder(order);
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("limit은 1~" + MAX_LIMIT + " 사이여야 합니다: " + limit);
        }
        return dailyTradingInformationJDBCRepository.findRsiRanking(targetDate, mktNm, asc, limit);
    }

    private static LocalDate parseDate(String date) {
        if (date == null || date.isBlank()) {
            throw new IllegalArgumentException("date는 필수입니다 (yyyyMMdd)");
        }
        try {
            return LocalDate.parse(date.trim(), YYYYMMDD);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("date 형식이 올바르지 않습니다 (yyyyMMdd): " + date);
        }
    }

    private static String normalizeMarket(String market) {
        if (market == null || !SUPPORTED_MARKETS.contains(market.trim().toUpperCase())) {
            throw new IllegalArgumentException("market은 KOSPI 또는 KOSDAQ이어야 합니다: " + market);
        }
        return market.trim().toUpperCase();
    }

    private static boolean parseOrder(String order) {
        if (order == null || order.isBlank() || "desc".equalsIgnoreCase(order.trim())) {
            return false;
        }
        if ("asc".equalsIgnoreCase(order.trim())) {
            return true;
        }
        throw new IllegalArgumentException("order는 asc 또는 desc여야 합니다: " + order);
    }
}
