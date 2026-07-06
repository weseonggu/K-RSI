package com.service.RSIranking.batch_test;

import com.service.RSIranking.dto.RSIMessageDTO;
import com.service.RSIranking.schedule.RSICalculationLauncher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.test.context.ActiveProfiles;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@SpringBootTest
@ActiveProfiles("test")
public class RSICalcBatchTest {
    @Autowired
    private RSICalculationLauncher rsiCalculationLauncher;
    // RSI 프로듀서 메세지 생성 100일치
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
    // RSI 프로듀서 메세지 생성 지정일
    @Test
    public void oneDayRsiProducerTest(){
        Integer[] marketDay = {
                20250709,
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
    private StreamMessageListenerContainer<String, MapRecord<String, String, String>> rsiStreamMessageListenerContainer;

    private static final String STREAM_KEY = "rsi:calculation:stream:KOSPI"; // 테스트용 스트림 키

    @Test
    void testConsumeSingleMessage() throws Exception {
        CompletableFuture<Void> future = new CompletableFuture<>();

        // 리스너 등록 (메시지 1개 처리 후 future 완료)
        rsiStreamMessageListenerContainer.receive(
                StreamOffset.fromStart(STREAM_KEY),
                (message) -> {
                    System.out.println("Received message: " + message.getValue());
                    future.complete(null); // 메시지 하나만 받고 종료
                }
        );

        // 컨테이너 시작
        rsiStreamMessageListenerContainer.start();

        // 최대 5초 기다리면서 메시지 수신
        future.get(5, TimeUnit.SECONDS);

        // 테스트 종료 후 컨테이너 정지
        rsiStreamMessageListenerContainer.stop();
    }
}
