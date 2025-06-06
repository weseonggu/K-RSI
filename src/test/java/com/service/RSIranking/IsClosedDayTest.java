package com.service.RSIranking;

import com.service.RSIranking.util.IsClosedDay;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

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
    public void test(){
        Integer marketDay[] = {
                20250602,
                20250530,
                20250529,
                20250528,
                20250527,
                20250526,
                20250523,
                20250522,
                20250521,
                20250520,
                20250519,
                20250516,
                20250515,
                20250514,
                20250513,
                20250512,
                20250509,
                20250508,
                20250507,
                20250502,
                20250501,
                20250430,
                20250429,
                20250428,
                20250425,
                20250424,
                20250423,
                20250422};
        for(Integer date : marketDay){
            boolean isClosed = isClosedDay.isClosedDay(date.toString());
            System.out.println("날짜: "+date+" "+isClosed);
        }

    }
}
