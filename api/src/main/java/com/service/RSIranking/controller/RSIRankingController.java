package com.service.RSIranking.controller;

import com.service.RSIranking.dto.PagedResponse;
import com.service.RSIranking.dto.RSIRankingDto;
import com.service.RSIranking.dto.StockSearchSuggestionDto;
import com.service.RSIranking.service.RSIRankingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 일별 RSI 순위 조회 REST API.
 *
 * <h2>사용 예</h2>
 * <pre>
 * GET /api/rsi/ranking?date=20260701&market=KOSPI                         → RSI 오름차순(과매도) 1페이지(50건)
 * GET /api/rsi/ranking?date=20260701&market=KOSDAQ&order=desc             → RSI 내림차순(과매수) 1페이지
 * GET /api/rsi/ranking?date=20260701&market=KOSPI&rsiMin=10&rsiMax=20     → RSI 10~20 구간(경계 포함) 필터
 * GET /api/rsi/ranking?date=20260701&market=KOSPI&page=1&size=20         → 2번째 페이지(0-base), 20건
 * </pre>
 *
 * @author RSIranking Team
 * @version 1.0
 */
@RestController
@RequestMapping("/api/rsi")
@RequiredArgsConstructor
public class RSIRankingController {

    private final RSIRankingService rsiRankingService;

    /**
     * 특정 날짜의 RSI 순위를 조회합니다.
     *
     * @param date   조회 날짜 (yyyyMMdd, 필수)
     * @param market 시장 구분 (KOSPI / KOSDAQ, 필수)
     * @param order  정렬 방향 (asc: 과매도 순 - 기본값 / desc: 과매수 순)
     * @param rsiMin RSI 하한 (선택, 0~100, 경계 포함)
     * @param rsiMax RSI 상한 (선택, 0~100, 경계 포함)
     * @param page   페이지 번호 (0-base, 기본 0)
     * @param size   페이지 크기 (기본 50, 1~500)
     * @return 페이지 응답 (items/page/size/totalElements/totalPages)
     */
    @GetMapping("/ranking")
    public ResponseEntity<PagedResponse<RSIRankingDto>> getRanking(
            @RequestParam("date") String date,
            @RequestParam("market") String market,
            @RequestParam(value = "order", defaultValue = "asc") String order,
            @RequestParam(value = "rsiMin", required = false) Double rsiMin,
            @RequestParam(value = "rsiMax", required = false) Double rsiMax,
            @RequestParam(value = "isuCd", required = false) String isuCd,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "50") int size) {
        if (isuCd == null || isuCd.isBlank()) {
            return ResponseEntity.ok(
                    rsiRankingService.getRanking(date, market, order, rsiMin, rsiMax, page, size));
        }
        return ResponseEntity.ok(
                rsiRankingService.getRanking(date, market, order, rsiMin, rsiMax, isuCd, page, size));
    }

    @GetMapping("/stocks/search")
    public List<StockSearchSuggestionDto> searchStocks(@RequestParam String market,
                                                       @RequestParam String keyword) {
        return rsiRankingService.searchStocks(market, keyword);
    }

    @GetMapping("/latest-date")
    public Map<String, LocalDate> latestDate(@RequestParam String market) {
        return Map.of("date", rsiRankingService.getLatestDate(market));
    }
}
