package com.service.RSIranking;

import com.service.RSIranking.util.IsHoliday;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class TestHoliday {
    @Autowired
    private IsHoliday isHoliday;

    @Test
    public void testGetAnniversaryInfo(){
        isHoliday.getAnniversaryInfo(2025,3);
    }
}
