package com.service.RSIranking.batch_test;

import com.service.RSIranking.schedule.DailyTradingInfoLauncher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
public class DailyTrandingbatchTest {
    @Autowired
    private DailyTradingInfoLauncher dailyTradingInfoLauncher;
    // 매매데이터 수집 100일치
    @Test
    public void tradingIngoUpdateTest() {

        long startTime = System.nanoTime(); // 시작 시간 측정

        try {
            for (Integer date : StockInfoBatchTest.marketDay) {
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

        System.out.println(String.format("총 실행 시간: %02d시간 %02d분 %02d초", hours, minutes, seconds));
    }

    // 지정 날짜 데이터 수집
    @Test
    public void oneDayTradingIngoUpdateTest() {

        long startTime = System.nanoTime(); // 시작 시간 측정
        Integer[] marketDay = {

        };
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

        System.out.println(String.format("총 실행 시간: %02d시간 %02d분 %02d초", hours, minutes, seconds));
    }
}
