package com.service.RSIranking.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * API 전역 예외 처리기.
 *
 * <p>컨트롤러마다 중복되던 {@code @ExceptionHandler}를 한곳으로 모아 관리합니다.
 * 응답 계약은 기존과 동일하게 {@code 400 + {"error": 메시지}} 형태를 유지합니다(프론트엔드 계약).</p>
 *
 * <p>{@code @RestControllerAdvice} 빈은 {@code @WebMvcTest} 슬라이스에도 포함되므로,
 * 각 컨트롤러 테스트의 에러 응답 검증(400 + {@code {"error": ...}})이 그대로 통과합니다.</p>
 *
 * @author RSIranking Team
 * @version 1.0
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    /**
     * 잘못된 요청 파라미터를 400으로 응답합니다.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
    }
}
