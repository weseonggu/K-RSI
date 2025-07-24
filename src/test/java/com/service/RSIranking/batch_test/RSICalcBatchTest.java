package com.service.RSIranking.batch_test;

import com.service.RSIranking.schedule.RSICalculationLauncher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
public class RSICalcBatchTest {
    @Autowired
    private RSICalculationLauncher rsiCalculationLauncher;
    // RSI 프로듀서 메세지 생성
    @Test
    public void rsiProducerTest(){

        try {
            for(Integer date: StockInfoBatchTest.marketDay){
                rsiCalculationLauncher.executeRSICalculation(date.toString());
                Thread.sleep(500);
            }

        }catch (Exception e){

        }
    }
}
