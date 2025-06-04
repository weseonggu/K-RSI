package com.service.RSIranking.util;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MarketDayForTheLast14Days {

    private final IsClosedDay isClosedDay;
    private final DateUtil dateUtil;

    public List<LocalDate> getMarketDayForTheLast14Days(String batchDate){
        List<LocalDate> marketDays = new ArrayList<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd");
        LocalDate currentDate = LocalDate.parse(batchDate, formatter);
        
        while (marketDays.size() < 13) {
            currentDate = currentDate.minusDays(1);
            String dateStr = currentDate.format(formatter);
            
            // 주말이 아닌 경우에만 휴장일 체크
            if (!dateUtil.isWeekend(dateStr)) {
                // 휴장일이 아닌 경우에만 리스트에 추가
                if (!isClosedDay.isClosedDay(dateStr)) {
                    marketDays.add(currentDate);
                }
            }
        }
        
        return marketDays;
    }

}
