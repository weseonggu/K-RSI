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
            stockInfoLauncher.infoUpdateJobLauncher("20250708");
        }catch (Exception e){
            System.out.println(e.getMessage());
        }

    }

    @Autowired
    private DailyTradingInfoLauncher dailyTradingInfoLauncher;
    // 매매데이터 수집
    @Test
    public void tradingIngoUpdateTest() {
        Integer[] marketDay = {
                20250708,
                20250707,
                20250704,
                20250703,
                20250702,
                20250701
        };

        long startTime = System.nanoTime(); // 시작 시간 측정

        try {
            for (Integer date : marketDay) {
                dailyTradingInfoLauncher.dailyTradingInfoJobLauncher(date.toString());
                Thread.sleep(60000);
            }
        } catch (Exception e) {
            System.out.println("예외 발생: " + e.getMessage());
        }

        long endTime = System.nanoTime(); // 종료 시간 측정
        long durationInMillis = (endTime - startTime) / 1_000_000; // ms 단위 변환

        System.out.println("업데이트한 날짜: " + marketDay.length);
        System.out.println("총 실행 시간: " + durationInMillis + "ms");
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


}
