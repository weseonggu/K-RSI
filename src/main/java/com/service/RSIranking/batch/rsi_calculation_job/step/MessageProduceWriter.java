package com.service.RSIranking.batch.rsi_calculation_job.step;

import com.service.RSIranking.dto.RSIMessageDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
@StepScope
@Component
@Slf4j
public class MessageProduceWriter implements ItemWriter<RSIMessageDTO> {

    private final RedisTemplate<String, Object> redisTemplate;
    private static final String STREAM_KEY = "rsi:calculation:stream:";
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd");

    public MessageProduceWriter(@Qualifier("rsiMessageRedisTemplate")RedisTemplate redisTemplate){
        this.redisTemplate = redisTemplate;
    }

//    @Override
//    public void write(Chunk<? extends RSIMessageDTO> chunk) throws Exception {
//        for (RSIMessageDTO message : chunk) {
//            try {
//
//                // todo 레디스 스트링에 메세지 저장이 MapRecord<String, String, String> -> ObjectRecord<String, RSIMessageDTO>
//
//                Map<String, String> messageMap = new HashMap<>();
//                messageMap.put("mkt_nm", message.getMkt_nm());
//                messageMap.put("isu_cd", message.getIsu_cd());
//                messageMap.put("targetDate", message.getTargetDate());
//
//
//
//                String marketDates = message.getMarketDate().stream()
//                        .map(date -> date.format(formatter))
//                        .collect(Collectors.joining(","));
//                messageMap.put("marketDate", marketDates);
//
//                MapRecord<String, String, String> record = StreamRecords.newRecord()
//                        .ofMap(messageMap)
//                        .withStreamKey(STREAM_KEY + message.getMkt_nm());
//
//                redisTemplate.opsForStream().add(record);
//                log.info("메시지가 Redis Stream에 저장되었습니다. 메시지: {}", message);
//            } catch (Exception e) {
//                log.error("Redis Stream에 메시지 저장 중 오류 발생: {}", e.getMessage(), e);
//                throw e;
//            }
//        }
//    }
    @Override
    public void write(Chunk<? extends RSIMessageDTO> chunk) throws Exception {
        for (RSIMessageDTO message : chunk) {
            // todo 레디스 스트링에 메세지 저장이 MapRecord<String, String, String> -> ObjectRecord<String, RSIMessageDTO> -> 데이터 깨짐 문제 발생
            try {
                ObjectRecord<String, RSIMessageDTO> record = StreamRecords.newRecord()
                        .ofObject(message)  // ✅ DTO 자체를 직렬화
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
