package com.service.RSIranking.consumer;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class RSICalCulationConsumer {

    private final RedisTemplate<String, Object> redisTemplate;
    private static final String STREAM_KEY_PREFIX = "rsi:calculation:stream:";
    private static final String KOSPI_STREAM = STREAM_KEY_PREFIX + "KOSPI";
    private static final String KOSDAQ_STREAM = STREAM_KEY_PREFIX + "KOSDAQ";
    private static final String CONSUMER_GROUP = "rsiGroup";
    private static final String CONSUMER_NAME = "rsiConsumer";
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd");

    public RSICalCulationConsumer(@Qualifier("rsiMessageRedisTemplate") RedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        initializeConsumerGroups();
    }

    private void initializeConsumerGroups() {
        try {
            redisTemplate.opsForStream().createGroup(KOSPI_STREAM, CONSUMER_GROUP);
            log.info("Consumer group created for KOSPI stream");
        } catch (Exception e) {
            log.info("Consumer group already exists for KOSPI stream");
        }

        try {
            redisTemplate.opsForStream().createGroup(KOSDAQ_STREAM, CONSUMER_GROUP);
            log.info("Consumer group created for KOSDAQ stream");
        } catch (Exception e) {
            log.info("Consumer group already exists for KOSDAQ stream");
        }
    }

//    @Scheduled(fixedDelay = 1000)
    public void consumeKospi() {
        log.info("Kospi컨슈머 실행");
        try {
            StreamInfo.XInfoStream streamInfo = redisTemplate.opsForStream().info(KOSPI_STREAM);
            log.info("KOSPI Stream Info - Length: {}, Groups: {}", 
                    streamInfo.streamLength(), 
                    streamInfo.groupCount());

            StreamReadOptions options = StreamReadOptions.empty()
                    .count(10)
                    .block(Duration.ofSeconds(1));

            List<MapRecord<String, Object, Object>> messages = redisTemplate.opsForStream()
                    .read(Consumer.from(CONSUMER_GROUP, CONSUMER_NAME),
                            options,
                            StreamOffset.create(KOSPI_STREAM, ReadOffset.lastConsumed()));

            if (messages == null || messages.isEmpty()) {
                log.info("No new messages in KOSPI stream");
                return;
            }

            log.info("Found {} messages in KOSPI stream", messages.size());
            
            for (MapRecord<String, Object, Object> message : messages) {
                try {
                    Map<Object, Object> value = message.getValue();
                    log.info("Processing KOSPI RSI message - isu_cd: {}, targetDate: {}, marketDate: {}", 
                            value.get("isu_cd"),
                            value.get("targetDate"),
                            value.get("marketDate"));

                    // 여기에 KOSPI RSI 계산 로직 추가
                    // processKospiRSIMessage(value);

                    // 처리 성공 시 ACK + 삭제
                    redisTemplate.opsForStream().acknowledge(KOSPI_STREAM, CONSUMER_GROUP, message.getId());
                    redisTemplate.opsForStream().delete(KOSPI_STREAM, message.getId());
                    log.info("KOSPI message processed and deleted: {}", message.getId());

                } catch (Exception e) {
                    log.error("Error processing KOSPI message: {}", e.getMessage(), e);
                    // 에러 발생 시 메시지를 삭제하지 않음 -> 재시도 가능
                }
            }
        } catch (Exception e) {
            log.error("Error reading from KOSPI stream: {}", e.getMessage(), e);
        }
    }

//    @Scheduled(fixedDelay = 1000)
    public void consumeKosdaq() {
        log.info("Kosdaq컨슈머 실행");
        try {
            StreamInfo.XInfoStream streamInfo = redisTemplate.opsForStream().info(KOSDAQ_STREAM);
            log.info("KOSDAQ Stream Info - Length: {}, Groups: {}", 
                    streamInfo.streamLength(), 
                    streamInfo.groupCount());

            StreamReadOptions options = StreamReadOptions.empty()
                    .count(10)
                    .block(Duration.ofSeconds(1));

            List<MapRecord<String, Object, Object>> messages = redisTemplate.opsForStream()
                    .read(Consumer.from(CONSUMER_GROUP, CONSUMER_NAME),
                            options,
                            StreamOffset.create(KOSDAQ_STREAM, ReadOffset.lastConsumed()));

            if (messages == null || messages.isEmpty()) {
                log.info("No new messages in KOSDAQ stream");
                return;
            }

            log.info("Found {} messages in KOSDAQ stream", messages.size());
            
            for (MapRecord<String, Object, Object> message : messages) {
                try {
                    Map<Object, Object> value = message.getValue();
                    log.info("Processing KOSDAQ RSI message - isu_cd: {}, targetDate: {}, marketDate: {}", 
                            value.get("isu_cd"),
                            value.get("targetDate"),
                            value.get("marketDate"));

                    // 여기에 KOSDAQ RSI 계산 로직 추가
                    // processKosdaqRSIMessage(value);

                    // 처리 성공 시 ACK + 삭제
                    redisTemplate.opsForStream().acknowledge(KOSDAQ_STREAM, CONSUMER_GROUP, message.getId());
                    redisTemplate.opsForStream().delete(KOSDAQ_STREAM, message.getId());
                    log.info("KOSDAQ message processed and deleted: {}", message.getId());

                } catch (Exception e) {
                    log.error("Error processing KOSDAQ message: {}", e.getMessage(), e);
                    // 에러 발생 시 메시지를 삭제하지 않음 -> 재시도 가능
                }
            }
        } catch (Exception e) {
            log.error("Error reading from KOSDAQ stream: {}", e.getMessage(), e);
        }
    }
}
