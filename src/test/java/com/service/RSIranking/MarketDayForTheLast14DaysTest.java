package com.service.RSIranking;

import com.service.RSIranking.util.MarketDayForTheLast14Days;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.util.List;

@SpringBootTest
public class MarketDayForTheLast14DaysTest {
    @Autowired
    private MarketDayForTheLast14Days marketDayForTheLast14Days;

    @Test
    public void test(){
        List<LocalDate> date = marketDayForTheLast14Days.getMarketDayForTheLast14Days("20250602");
        date.forEach(date1 -> System.out.println(date1.toString()));
    }
}
