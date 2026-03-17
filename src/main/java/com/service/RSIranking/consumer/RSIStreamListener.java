package com.service.RSIranking.consumer;

import com.service.RSIranking.service.RSICalculationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * RSI 메시지 Stream Listener.
 *
 * <p>Redis Stream에서 수신된 RSI 메시지를 처리하는 리스너입니다.
 * MapRecord 형태의 메시지를 수신하여 RSI 계산 서비스를 호출합니다.</p>
 *
 * @author RSIranking Team
 * @version 1.1
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class RSIStreamListener implements StreamListener<String, MapRecord<String, String, String>> {

    private final RSICalculationService rsiCalculationService;

    /**
     * Redis Stream에서 메시지를 수신했을 때 호출됩니다.
     *
     * @param message 수신된 RSI 메시지 (MapRecord)
     */
    @Override
    public void onMessage(MapRecord<String, String, String> message) {
        Map<String, String> value = message.getValue();
        String mktNm = value.get("mkt_nm");
        String isuCd = value.get("isu_cd");
        String targetDate = value.get("targetDate");
        String marketDate = value.get("marketDate");

        log.info("시장 구분: {} 종목 코드: {} 대상 날짜: {}", mktNm, isuCd, targetDate);

        try {
            rsiCalculationService.rsiCalculation(isuCd, targetDate, marketDate);
        } catch (Exception e) {
            log.error("RSI 계산 처리 실패 - 종목: {}, 날짜: {}, 오류: {}", isuCd, targetDate, e.getMessage(), e);
        }
    }
}
