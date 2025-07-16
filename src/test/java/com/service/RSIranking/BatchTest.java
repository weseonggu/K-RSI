package com.service.RSIranking;

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
    // 종목 업데이트
    @Test
    public void stockInfoUpdateTest(){
        try {
            stockInfoLauncher.infoUpdateJobLauncher("20250715");
        }catch (Exception e){
            System.out.println(e.getMessage());
        }

    }

    @Autowired
    private DailyTradingInfoLauncher dailyTradingInfoLauncher;
    // 매매데이터 수집
    @Test
    public void tradingIngoUpdateTest() {

        long startTime = System.nanoTime(); // 시작 시간 측정

        try {
            for (Integer date : marketDay) {
                dailyTradingInfoLauncher.dailyTradingInfoJobLauncher(date.toString());
                Thread.sleep(1000);
            }
        } catch (Exception e) {
            System.out.println("예외 발생: " + e.getMessage());
        }

        long endTime = System.nanoTime(); // 종료 시간 측정
        long durationInMillis = (endTime - startTime) / 1_000_000;

        long seconds = durationInMillis / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;

        seconds %= 60;
        minutes %= 60;

        System.out.println("업데이트한 날짜: " + marketDay.length);
        System.out.println(String.format("총 실행 시간: %02d시간 %02d분 %02d초", hours, minutes, seconds));
    }


    @Autowired
    private RSICalculationLauncher rsiCalculationLauncher;
    // RSI 프로듀서 메세지 생성
    @Test
    public void rsiProducerTest(){
        Integer marketDay[] = {
                20250611
        };

        try {
            for(Integer date: marketDay){
                rsiCalculationLauncher.executeRSICalculation(date.toString());
                Thread.sleep(500);
            }

        }catch (Exception e){

        }
    }

    private Integer[] marketDay = {
            20250715,
            20250714,
            20250711,
            20250710,
            20250709,
            20250708,
            20250707,
            20250704,
            20250703,
            20250702,
            20250701,
            20250630,
            20250627,
            20250626,
            20250625,
            20250624,
            20250623,
            20250620,
            20250619,
            20250618,
            20250617,
            20250616,
            20250613,
            20250612,
            20250611,
            20250610,
            20250609,
            20250605,
            20250604,
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
    };
}
