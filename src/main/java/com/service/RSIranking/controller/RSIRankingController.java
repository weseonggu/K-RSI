package com.service.RSIranking.controller;

import com.service.RSIranking.dto.RSIRankingDto;
import com.service.RSIranking.service.RSIRankingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 일별 RSI 순위 조회 REST API.
 *
 * <h2>사용 예</h2>
 * <pre>
 * GET /api/rsi/ranking?date=20260701&market=KOSPI              → RSI 내림차순(과매수) 상위 50
 * GET /api/rsi/ranking?date=20260701&market=KOSDAQ&order=asc   → RSI 오름차순(과매도) 상위 50
 * GET /api/rsi/ranking?date=20260701&market=KOSPI&limit=100    → 상위 100
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
     * @param order  정렬 방향 (desc: 과매수 순 - 기본값 / asc: 과매도 순)
     * @param limit  최대 조회 건수 (기본 50, 최대 500)
     * @return RSI 순위 목록
     */
    @GetMapping("/ranking")
    public ResponseEntity<List<RSIRankingDto>> getRanking(
            @RequestParam("date") String date,
            @RequestParam("market") String market,
            @RequestParam(value = "order", defaultValue = "desc") String order,
            @RequestParam(value = "limit", defaultValue = "50") int limit) {
        return ResponseEntity.ok(rsiRankingService.getRanking(date, market, order, limit));
    }

    /**
     * 잘못된 요청 파라미터를 400으로 응답합니다.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
    }
}
