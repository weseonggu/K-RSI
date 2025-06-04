package com.service.RSIranking;

import com.service.RSIranking.consumer.RSICalCulationConsumer;
import com.service.RSIranking.schedule.DailyTradingInfoLauncher;
import com.service.RSIranking.schedule.RSICalculationLauncher;
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

    @Autowired
    private DailyTradingInfoLauncher dailyTradingInfoLauncher;

    @Test
    public void tradingIngoUpdateTest(){
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
        try{
            for(Integer date: marketDay){
                dailyTradingInfoLauncher.dailyTradingInfoJobLauncher(date.toString());
                Thread.sleep(5000);
            }
        }catch (Exception e){

        }
    }

    @Autowired
    private RSICalculationLauncher rsiCalculationLauncher;

    @Test
    public void rsiProducerTest(){
        try {
            rsiCalculationLauncher.RSICalculationSchedule();

        }catch (Exception e){

        }
    }

    @Autowired
    private RSICalCulationConsumer rsiCalCulationConsumer;

    @Test
    public void consumerTest(){
        long start = System.nanoTime();

        rsiCalCulationConsumer.consumeKospi();
        rsiCalCulationConsumer.consumeKosdaq();

        long end = System.nanoTime();
        long durationInMillis = (end - start) / 1_000_000;
        System.out.println("Execution Time: " + durationInMillis + " ms");
    }

}
