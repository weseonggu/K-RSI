package com.service.RSIranking;

import com.service.RSIranking.util.DateUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
@SpringBootTest
public class DateUtilTest {
    @Autowired
    private DateUtil dateUtil;

    @Test
    public void weekendTest(){
        boolean result = dateUtil.isWeekend("20250601");// 일요일 날짜
        assertEquals(true, result);
    }

    @Test
    public void weekdayTest(){
        boolean result = dateUtil.isWeekend("20250602");// 월요일 날짜
        assertEquals(false, result);
    }
}
