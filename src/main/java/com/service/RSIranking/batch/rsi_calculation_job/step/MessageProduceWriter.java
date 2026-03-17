package com.service.RSIranking.batch.rsi_calculation_job.step;

import com.service.RSIranking.dto.RSIMessageDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * RSI 계산 메시지 Redis Stream 발행 Writer.
 *
 * <p>RSI 계산 메시지 DTO를 Redis Stream에 발행합니다.
 * KOSPI와 KOSDAQ 메시지는 각각 별도의 스트림에 저장됩니다.</p>
 *
 * <h2>스트림 키 구조</h2>
 * <ul>
 *   <li>KOSPI: {@code rsi:calculation:stream:KOSPI}</li>
 *   <li>KOSDAQ: {@code rsi:calculation:stream:KOSDAQ}</li>
 * </ul>
 *
 * <h2>메시지 형식</h2>
 * <p>MapRecord 형태로 저장하여 안정적인 직렬화/역직렬화를 보장합니다.</p>
 *
 * @author RSIranking Team
 * @version 1.1
 */
@StepScope
@Component
@Slf4j
public class MessageProduceWriter implements ItemWriter<RSIMessageDTO> {

    private final RedisTemplate<String, Object> redisTemplate;
    private static final String STREAM_KEY = "rsi:calculation:stream:";
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd");

    public MessageProduceWriter(@Qualifier("rsiMessageRedisTemplate") RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * RSI 계산 메시지를 Redis Stream에 발행합니다.
     *
     * @param chunk 발행할 메시지 청크
     * @throws Exception 발행 중 오류 발생 시
     */
    @Override
    public void write(Chunk<? extends RSIMessageDTO> chunk) throws Exception {
        for (RSIMessageDTO message : chunk) {
            try {
                Map<String, String> messageMap = new HashMap<>();
                messageMap.put("mkt_nm", message.getMkt_nm());
                messageMap.put("isu_cd", message.getIsu_cd());
                messageMap.put("targetDate", message.getTargetDate());

                String marketDates = message.getMarketDate().stream()
                        .map(date -> date.format(formatter))
                        .collect(Collectors.joining(","));
                messageMap.put("marketDate", marketDates);

                MapRecord<String, String, String> record = StreamRecords.newRecord()
                        .ofMap(messageMap)
                        .withStreamKey(STREAM_KEY + message.getMkt_nm());

                redisTemplate.opsForStream().add(record);
                log.info("메시지가 Redis Stream에 저장되었습니다. 메시지: {}", message);
            } catch (Exception e) {
                log.error("Redis Stream에 메시지 저장 중 오류 발생: {}", e.getMessage(), e);
                throw e;
            }
        }
    }
}
