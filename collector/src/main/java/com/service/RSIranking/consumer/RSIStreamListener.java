package com.service.RSIranking.consumer;

import com.service.RSIranking.service.RSICalculationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * RSI 메시지 Stream Listener.
 *
 * <p>Redis Stream에서 수신된 RSI 메시지를 처리하는 리스너입니다.
 * MapRecord 형태의 메시지를 수신하여 RSI 계산 서비스를 호출합니다.</p>
 *
 * <h2>ACK / XDEL 정책</h2>
 * <ul>
 *   <li>처리 성공: 해당 그룹에 ACK 후 stream에서 XDEL → 누적 방지.</li>
 *   <li>처리 실패: ACK/XDEL 없음 → 그룹 PEL(Pending Entries List)에 남아 reclaim/재처리 가능.</li>
 * </ul>
 * <p>Container 옵션의 {@code autoAcknowledge}는 미설정(기본 false)이므로 수동 ACK가 필수.</p>
 *
 * @author RSIranking Team
 * @version 1.2
 */
@Component
@Slf4j
public class RSIStreamListener implements StreamListener<String, MapRecord<String, String, String>> {

    private static final String KOSPI_GROUP = "RSI-Kospi-Group";
    private static final String KOSDAQ_GROUP = "RSI-Kosdaq-Group";
    private static final String ETF_GROUP = "RSI-Etf-Group";

    private final RSICalculationService rsiCalculationService;
    private final RedisTemplate<String, Object> redisTemplate;

    public RSIStreamListener(RSICalculationService rsiCalculationService,
                             @Qualifier("rsiMessageRedisTemplate") RedisTemplate<String, Object> redisTemplate) {
        this.rsiCalculationService = rsiCalculationService;
        this.redisTemplate = redisTemplate;
    }

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
            rsiCalculationService.rsiCalculation(isuCd, targetDate, marketDate, mktNm);

            // 처리 성공 시 ACK + XDEL (스트림/PEL 누적 방지)
            String groupName = resolveGroupName(mktNm);
            String streamKey = message.getStream();
            try {
                redisTemplate.opsForStream().acknowledge(streamKey, groupName, message.getId());
                redisTemplate.opsForStream().delete(streamKey, message.getId());
            } catch (Exception ackEx) {
                // ack/del 실패는 비즈니스 로직 결과를 무효화하지 않으므로 경고만 남김
                log.warn("ACK/XDEL 실패 - stream: {}, group: {}, id: {}, 오류: {}",
                        streamKey, groupName, message.getId(), ackEx.getMessage());
            }
        } catch (Exception e) {
            log.error("RSI 계산 처리 실패 - 종목: {}, 날짜: {}, 오류: {}", isuCd, targetDate, e.getMessage(), e);
            // 실패 시 ack/del 하지 않음 → 그룹 PEL에 남아 reclaim/재처리 가능
        }
    }

    /**
     * 시장 구분에 해당하는 consumer group 이름을 반환합니다.
     * (group 이름은 {@code RSIRedisStreamConsumerService}와 일치해야 함)
     */
    private static String resolveGroupName(String mktNm) {
        if (mktNm == null) {
            throw new IllegalArgumentException("mktNm must not be null");
        }
        return switch (mktNm.trim().toUpperCase()) {
            case "KOSPI" -> KOSPI_GROUP;
            case "KOSDAQ" -> KOSDAQ_GROUP;
            case "ETF" -> ETF_GROUP;
            default -> throw new IllegalArgumentException("Unsupported mktNm: " + mktNm);
        };
    }
}
