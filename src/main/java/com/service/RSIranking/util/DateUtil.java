package com.service.RSIranking.util;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 날짜 관련 유틸리티 서비스.
 *
 * <p>배치 작업에서 사용되는 날짜 관련 기능을 제공합니다.</p>
 *
 * <p>주요 기능:</p>
 * <ul>
 *   <li>어제 날짜 조회</li>
 *   <li>주말 여부 판별</li>
 * </ul>
 *
 * @author RSIranking Team
 * @version 1.0
 */
@Service
@RequiredArgsConstructor
public class DateUtil {

    /**
     * 어제 날짜 구하기
     * @return yyyyMMdd 날짜 문자열
     */
    public String yesterday(){
        return LocalDate.now().minusDays(1).format(DateTimeFormatter.ofPattern("yyyyMMdd"));
    }

    /**
     * 주어진 날짜가 주말인지 확인합니다.
     *
     * <p>토요일 또는 일요일인 경우 true를 반환합니다.</p>
     *
     * @param date 확인할 날짜 (yyyyMMdd 형식)
     * @return 주말이면 true, 평일이면 false
     */
    public boolean isWeekend(String date) {
        try {
            // 날짜 문자열을 LocalDate로 파싱
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd");
            LocalDate localDate = LocalDate.parse(date, formatter);

            // 요일 확인
            DayOfWeek dayOfWeek = localDate.getDayOfWeek();
            return dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY;
        } catch (Exception e) {
            // 잘못된 날짜 형식 처리
            System.err.println("Invalid date format: " + date);
            return false;
        }
    }

}
