package com.service.RSIranking.service;

import com.service.RSIranking.dto.PagedResponse;
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
    private static final int MAX_SIZE = 500;

    private final DailyTradingInformationJDBCRepository dailyTradingInformationJDBCRepository;

    /**
     * 특정 날짜의 RSI 순위를 조회합니다.
     *
     * <p>초과 페이지(요청한 {@code page}가 마지막 페이지를 넘는 경우)는 에러가 아니라
     * {@code items}가 빈 리스트인 정상 응답으로 처리됩니다. {@code totalElements}/{@code totalPages}는
     * 필터가 적용된 전체 기준으로 항상 정확하게 계산됩니다.</p>
     *
     * @param date   조회 날짜 (yyyyMMdd)
     * @param market 시장 구분 ("KOSPI" / "KOSDAQ", 대소문자 무관)
     * @param order  정렬 방향 ("asc": 과매도 순 - 기본값 / "desc": 과매수 순)
     * @param rsiMin RSI 하한 (nullable, 0~100)
     * @param rsiMax RSI 상한 (nullable, 0~100)
     * @param page   페이지 번호 (0-base, 0 이상)
     * @param size   페이지 크기 (1~500)
     * @return 페이지 응답 (RSI가 계산된 종목만)
     * @throws IllegalArgumentException 파라미터가 유효하지 않은 경우
     */
    public PagedResponse<RSIRankingDto> getRanking(String date, String market, String order,
                                                   Double rsiMin, Double rsiMax, int page, int size) {
        LocalDate targetDate = parseDate(date);
        String mktNm = normalizeMarket(market);
        boolean asc = parseOrder(order);
        validateRsiRange(rsiMin, rsiMax);
        validatePage(page);
        validateSize(size);

        // page(int) * size(int)를 int로 계산하면 오버플로될 수 있으므로, page를 먼저 long으로
        // 캐스팅한 뒤 곱한다(page 상한을 두지 않는 대신 오버플로 자체를 원천 차단).
        long offset = (long) page * size;
        List<RSIRankingDto> items = dailyTradingInformationJDBCRepository
                .findRsiRanking(targetDate, mktNm, asc, rsiMin, rsiMax, offset, size);
        long totalElements = dailyTradingInformationJDBCRepository
                .countRsiRanking(targetDate, mktNm, rsiMin, rsiMax);
        int totalPages = (int) Math.ceil(totalElements / (double) size);

        return new PagedResponse<>(items, page, size, totalElements, totalPages);
    }

    private static void validateRsiRange(Double rsiMin, Double rsiMax) {
        if (rsiMin != null && (rsiMin < 0 || rsiMin > 100)) {
            throw new IllegalArgumentException("rsiMin은 0~100 사이여야 합니다: " + rsiMin);
        }
        if (rsiMax != null && (rsiMax < 0 || rsiMax > 100)) {
            throw new IllegalArgumentException("rsiMax는 0~100 사이여야 합니다: " + rsiMax);
        }
        if (rsiMin != null && rsiMax != null && rsiMin > rsiMax) {
            throw new IllegalArgumentException("rsiMin은 rsiMax보다 클 수 없습니다: rsiMin=" + rsiMin + ", rsiMax=" + rsiMax);
        }
    }

    private static void validatePage(int page) {
        if (page < 0) {
            throw new IllegalArgumentException("page는 0 이상이어야 합니다: " + page);
        }
    }

    private static void validateSize(int size) {
        if (size < 1 || size > MAX_SIZE) {
            throw new IllegalArgumentException("size는 1~" + MAX_SIZE + " 사이여야 합니다: " + size);
        }
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
        if (order == null || order.isBlank() || "asc".equalsIgnoreCase(order.trim())) {
            return true; // 기본값: RSI 오름차순(과매도 순)
        }
        if ("desc".equalsIgnoreCase(order.trim())) {
            return false;
        }
        throw new IllegalArgumentException("order는 asc 또는 desc여야 합니다: " + order);
    }
}
