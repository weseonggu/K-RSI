package com.service.RSIranking.batch.rsi_calculation_job.step;

import com.service.RSIranking.dto.RSIMessageDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.RedisTemplate;

@Slf4j
public class MessageProduceWriter implements ItemWriter<RSIMessageDTO> {

    private final RedisTemplate<String, Object> redisTemplate;
    private static final String STREAM_KEY = "rsi:calculation:stream:";

    public MessageProduceWriter(RedisTemplate redisTemplate){
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void write(Chunk<? extends RSIMessageDTO> chunk) throws Exception {
        for (RSIMessageDTO message : chunk) {
            try {
                ObjectRecord<String, RSIMessageDTO> record = StreamRecords.newRecord()
                        .ofObject(message)
                        .withStreamKey(STREAM_KEY+message.getMkt_nm());
                
                redisTemplate.opsForStream().add(record);
                log.info("메시지가 Redis Stream에 저장되었습니다. 메시지: {}", message);
            } catch (Exception e) {
                log.error("Redis Stream에 메시지 저장 중 오류 발생: {}", e.getMessage(), e);
                throw e;
            }
        }
    }
}
