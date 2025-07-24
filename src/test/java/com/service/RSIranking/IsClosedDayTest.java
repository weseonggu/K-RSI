package com.service.RSIranking;

import com.service.RSIranking.util.IsClosedDay;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
@SpringBootTest
public class IsClosedDayTest {

    @Autowired
    private IsClosedDay isClosedDay;

    @Test
    public void setIsClosedDayTestTrue(){
        boolean isClosed = isClosedDay.isClosedDay("20250525");
        assertEquals(true, isClosed);
    }

    @Test
    public void setIsClosedDayTestFalse(){
        boolean isClosed = isClosedDay.isClosedDay("20250523");
        assertEquals(false, isClosed);
    }

    @Test
    public void testFindMarketDays() {
        List<Integer> openMarketDays = new ArrayList<>();
        LocalDate date = LocalDate.now().minusDays(1); // 어제부터 시작

        while (openMarketDays.size() < 100) {
            int formattedDate = Integer.parseInt(date.format(DateTimeFormatter.ofPattern("yyyyMMdd")));
            boolean isClosed = isClosedDay.isClosedDay(String.valueOf(formattedDate));

            if (!isClosed) {
                openMarketDays.add(formattedDate);
            }

            date = date.minusDays(1);
        }

        // 출력
        System.out.println("최근 100일간의 거래일:");
        for (Integer marketDay : openMarketDays) {
            System.out.println(marketDay+",");
        }
    }
}
