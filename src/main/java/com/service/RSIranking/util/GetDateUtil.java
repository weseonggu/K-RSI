package com.service.RSIranking.util;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
public class GetDateUtil {

    /**
     * 어제 날짜 구하기
     * @return yyyyMMdd 날짜 문자열
     */
    public String yesterday(){
        return LocalDate.now().minusDays(1).format(DateTimeFormatter.ofPattern("yyyyMMdd"));
    }

}
