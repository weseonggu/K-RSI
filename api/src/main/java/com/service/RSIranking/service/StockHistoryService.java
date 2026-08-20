package com.service.RSIranking.service;

import com.service.RSIranking.dto.StockHistoryItemDto;
import com.service.RSIranking.dto.StockHistoryResponse;
import com.service.RSIranking.repository.jdbc.DailyTradingInformationJDBCRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Set;

/**
 * 종목 일자별 시계열(OHLCV+RSI) 조회 서비스.
 *
 * <p>배치가 적재한 개별 종목의 일봉 데이터를 기준일 이하 최근 N 거래일 형태로 조회합니다.
 * 랭킹 조회({@link RSIRankingService})와 달리 RSI 미계산/거래정지 구간도 그대로 반환합니다(계획서 4.2절).</p>
 *
 * <p>계획서 3.2절에 따라 {@code normalizeMarket}/{@code parseDate}는 {@link RSIRankingService}와
 * 공유하지 않고 동일 패턴의 별도 private 메서드로 중복 구현합니다.</p>
 *
 * @author RSIranking Team
 * @version 1.0
 */
@Service
@RequiredArgsConstructor
public class StockHistoryService {

    private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final Set<String> SUPPORTED_MARKETS = Set.of("KOSPI", "KOSDAQ", "ETF");
    private static final int DEFAULT_DAYS = 120;
    private static final int MIN_DAYS = 1;
    private static final int MAX_DAYS = 1500;

    private final DailyTradingInformationJDBCRepository dailyTradingInformationJDBCRepository;

    /**
     * 특정 종목의 기준일 이하 최근 N 거래일 시계열을 조회합니다.
     *
     * @param isuCd  종목 코드 (필수, blank 불가)
     * @param market 시장 구분 ("KOSPI" / "KOSDAQ", 대소문자 무관)
     * @param date   기준일 (yyyyMMdd, 필수)
     * @param days   최근 거래일 개수 (nullable → 기본값 120, 허용 범위 1~1500)
     * @return 종목 시계열 응답
     * @throws IllegalArgumentException 파라미터가 유효하지 않은 경우
     */
    public StockHistoryResponse getHistory(String isuCd, String market, String date, Integer days) {
        validateIsuCd(isuCd);
        String mktNm = normalizeMarket(market);
        LocalDate anchorDate = parseDate(date);
        int resolvedDays = validateDays(days);

        List<StockHistoryItemDto> items =
                dailyTradingInformationJDBCRepository.findHistory(isuCd, mktNm, anchorDate, resolvedDays);

        return new StockHistoryResponse(isuCd, mktNm, items);
    }

    private static void validateIsuCd(String isuCd) {
        if (isuCd == null || isuCd.isBlank()) {
            throw new IllegalArgumentException("isuCd는 필수입니다");
        }
    }

    private static int validateDays(Integer days) {
        if (days == null) {
            return DEFAULT_DAYS;
        }
        if (days < MIN_DAYS || days > MAX_DAYS) {
            throw new IllegalArgumentException("days는 " + MIN_DAYS + "~" + MAX_DAYS + " 사이여야 합니다: " + days);
        }
        return days;
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
            throw new IllegalArgumentException("market은 KOSPI, KOSDAQ 또는 ETF여야 합니다: " + market);
        }
        return market.trim().toUpperCase();
    }
}
