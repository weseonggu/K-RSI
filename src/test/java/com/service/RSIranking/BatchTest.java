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
                20250501,// 근로자의 날 휴장
                20250430,
                20250429,
                20250428,
                20250425,
                20250424,
                20250423,
                20250422,
                20250421,
                20250418,
                20250417,
                20250416,
                20250415,
                20250414,
                20250411,
                20250410,
                20250409,
                20250408,
                20250407,
                20250404,
                20250403,
                20250402,
                20250401,
                20250331,
                20250328,
                20250327,
                20250326,
                20250325,
                20250324,
                20250321,
                20250320,
                20250319,
                20250318,
                20250317,
                20250314,
                20250313,
                20250312,
                20250311,
                20250310,
                20250307,
                20250306,
                20250305,
                20250304,
                20250228,
                20250227,
                20250226,
                20250225,
                20250224,
                20250221,
                20250220,
                20250219,
                20250218,
                20250217,
                20250214,
                20250213,
                20250212,
                20250211,
                20250210,
                20250207,
                20250206,
                20250205,
                20250204,
                20250203,
                20250131,
                20250124,
                20250123,
                20250122,
                20250121,
                20250120,
                20250117,
                20250116,
                20250115,
                20250114,
                20250113,
                20250110,
                20250109,
                20250108,
                20250107,
                20250106,
                20250103,
                20250102
        };
        try{
            for(Integer date: marketDay){
                dailyTradingInfoLauncher.dailyTradingInfoJobLauncher(date.toString());
                Thread.sleep(500);
            }
        }catch (Exception e){
            System.out.println(e);
        }
        System.out.println("업데이트한 날짜: "+marketDay.length);
    }

    @Autowired
    private RSICalculationLauncher rsiCalculationLauncher;

    @Test
    public void rsiProducerTest(){
        Integer marketDay[] = {
//                20250121,
//                20250122,
//                20250123,
//                20250124,
//                20250131,
//                20250203,
//                20250204,
//                20250205,
//                20250206,
//                20250207,
//                20250210,
//                20250211,
//                20250212,
//                20250213,
//                20250214,
//                20250217,
//                20250218,
//                20250219,
//                20250220,
//                20250221,
//                20250224,
//                20250225,
//                20250226,
//                20250227,
//                20250228,
//                20250304,
//                20250305,
//                20250306,
//                20250307,
//                20250310,
//                20250311,
//                20250312,
//                20250313,
//                20250314,
//                20250317,
//                20250318,
//                20250319,
//                20250320,
//                20250321,
//                20250324,
//                20250325,
//                20250326,
//                20250327,
//                20250328,
//                20250331,
//                20250401,
//                20250402,
//                20250403,
//                20250404,
//                20250407,
//                20250408,
//                20250409,
//                20250410,
//                20250411,
//                20250414,
//                20250415,
//                20250416,
//                20250417,
//                20250418,
//                20250421,
//                20250422,
//                20250423,
//                20250424,
//                20250425,
//                20250428,
//                20250429,
//                20250430,
//                20250502,
//                20250507,
                20250508,
                20250509,
                20250512,
                20250513,
                20250514,
                20250515,
                20250516,
                20250519,
                20250520,
                20250521,
                20250522,
                20250523,
                20250526,
                20250527,
                20250528,
                20250529,
                20250530,
                20250602
        };

        try {
            for(Integer date: marketDay){
                rsiCalculationLauncher.executeRSICalculation(date.toString());
                Thread.sleep(500);
            }

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
