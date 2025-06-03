package com.service.RSIranking;

import com.service.RSIranking.schedule.StockInfoLauncher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class BatchTest {

    @Autowired
    private StockInfoLauncher stockInfoLauncher;

    @Test
    public void stockInfoUpdateTest(){
        try {
            stockInfoLauncher.infoUpdateJobLauncher("20250602");
        }catch (Exception e){
            System.out.println(e.getMessage());
        }

    }

}
