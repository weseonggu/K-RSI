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
}
