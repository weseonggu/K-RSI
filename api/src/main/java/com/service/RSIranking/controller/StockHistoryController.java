package com.service.RSIranking.controller;

import com.service.RSIranking.dto.StockHistoryResponse;
import com.service.RSIranking.service.StockHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 종목 일자별 시계열(OHLCV+RSI) 조회 REST API.
 *
 * <h2>사용 예</h2>
 * <pre>
 * GET /api/rsi/stock/A00001/history?market=KOSPI&amp;date=20260709            → 기준일 이하 최근 120 거래일(기본)
 * GET /api/rsi/stock/A00001/history?market=KOSPI&amp;date=20260709&amp;days=250 → 최근 250 거래일
 * </pre>
 *
 * <p>{@code days}의 기본값 책임은 서비스가 소유합니다. 컨트롤러는 {@code required=false Integer days}로
 * 받아 null을 그대로 서비스에 위임합니다(계획서 리뷰어 필수 수정 2).</p>
 *
 * <p>예외 처리는 계획서 8장 확정안에 따라 {@link RSIRankingController}와 동일한 자체
 * {@code @ExceptionHandler}를 복제해 사용합니다(공통 {@code ApiExceptionHandler} 미도입,
 * {@link RSIRankingController} 미수정).</p>
 *
 * @author RSIranking Team
 * @version 1.0
 */
@RestController
@RequestMapping("/api/rsi")
@RequiredArgsConstructor
public class StockHistoryController {

    private final StockHistoryService stockHistoryService;

    /**
     * 특정 종목의 기준일 이하 최근 N 거래일 시계열을 조회합니다.
     *
     * @param isuCd  종목 코드 (path, 필수)
     * @param market 시장 구분 (KOSPI / KOSDAQ, 필수)
     * @param date   기준일 (yyyyMMdd, 필수)
     * @param days   최근 거래일 개수 (선택, null이면 서비스가 기본값 120 부여)
     * @return 종목 시계열 응답
     */
    @GetMapping("/stock/{isuCd}/history")
    public ResponseEntity<StockHistoryResponse> getHistory(
            @PathVariable("isuCd") String isuCd,
            @RequestParam("market") String market,
            @RequestParam("date") String date,
            @RequestParam(value = "days", required = false) Integer days) {
        return ResponseEntity.ok(stockHistoryService.getHistory(isuCd, market, date, days));
    }

    /**
     * 잘못된 요청 파라미터를 400으로 응답합니다.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
    }
}
