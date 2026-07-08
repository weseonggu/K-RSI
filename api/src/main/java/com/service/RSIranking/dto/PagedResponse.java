package com.service.RSIranking.dto;

import java.util.List;

/**
 * 페이지 응답 공용 래퍼.
 *
 * @param items         현재 페이지 항목 목록
 * @param page          현재 페이지 번호 (0-base)
 * @param size          페이지 크기
 * @param totalElements 필터 적용 후 전체 건수
 * @param totalPages    전체 페이지 수
 * @param <T>           항목 타입
 *
 * @author RSIranking Team
 * @version 1.0
 */
public record PagedResponse<T>(
        List<T> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
