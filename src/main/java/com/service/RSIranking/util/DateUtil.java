package com.service.RSIranking.util;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

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
