package com.service.RSIranking.consumer;

import com.service.RSIranking.service.RSICalculationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * RSI 계산 메시지 Consumer 서비스.
 *
 * <p>Redis Stream에서 RSI 계산 메시지를 소비하고,
 * RSI 계산 서비스를 호출하여 지표를 계산합니다.</p>
 *
 * <h2>처리 흐름</h2>
 * <ol>
 *   <li>Redis Stream에서 메시지 읽기 (Consumer Group 사용)</li>
 *   <li>RSI 계산 서비스 호출</li>
 *   <li>처리 완료 시 ACK 및 메시지 삭제</li>
 * </ol>
 *
 * <h2>Consumer Group</h2>
 * <ul>
 *   <li>그룹명: rsiGroup</li>
 *   <li>Consumer명: rsiConsumer</li>
 * </ul>
 *
 * <h2>활성화 조건</h2>
 * <p>{@code scheduler.rsiconsumer.enabled=true} 설정 시 활성화됩니다.</p>
 *
 * @author RSIranking Team
 * @version 1.0
 */
@Service
@Slf4j
@ConditionalOnProperty(name = "scheduler.rsiconsumer.enabled", havingValue = "true", matchIfMissing = false)
public class RSICalCulationConsumer {

    private final RedisTemplate<String, Object> redisTemplate;
    private final RSICalculationService rsiCalculationService;
    private static final String STREAM_KEY_PREFIX = "rsi:calculation:stream:";
    private static final String KOSPI_STREAM = STREAM_KEY_PREFIX + "KOSPI";
    private static final String KOSDAQ_STREAM = STREAM_KEY_PREFIX + "KOSDAQ";
    private static final String CONSUMER_GROUP = "rsiGroup";
    private static final String CONSUMER_NAME = "rsiConsumer";
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd");

    public RSICalCulationConsumer(@Qualifier("rsiMessageRedisTemplate") RedisTemplate redisTemplate,
                                  RSICalculationService rsiCalculationService) {
        this.redisTemplate = redisTemplate;
        this.rsiCalculationService = rsiCalculationService;
        initializeConsumerGroups();
    }

    /**
     * KOSPI, KOSDAQ 스트림에 대한 Consumer Group을 초기화합니다.
     */
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

    /**
     * KOSPI 스트림에서 RSI 계산 메시지를 소비합니다.
     */
    @Scheduled(fixedDelay = 1000)
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
                    log.info("KOSPI RSI 메세지 - isu_cd: {}, targetDate: {}, marketDate: {}",
                            value.get("isu_cd"),
                            value.get("targetDate"),
                            value.get("marketDate"));

                    // 여기에 KOSPI RSI 계산 로직 추가
                    callRSICalculationService(value.get("isu_cd").toString(),
                            value.get("targetDate").toString(),
                            value.get("marketDate").toString());
                    // processKospiRSIMessage(value);

                    // 처리 성공 시 ACK + 삭제
                    redisTemplate.opsForStream().acknowledge(KOSPI_STREAM, CONSUMER_GROUP, message.getId());
                    redisTemplate.opsForStream().delete(KOSPI_STREAM, message.getId());
                    log.info("KOSPI 메세지 처리 후 삭제: {}", message.getId());

                } catch (Exception e) {
                    log.error("Error processing KOSPI message: {}", e.getMessage(), e);
                    // 에러 발생 시 메시지를 삭제하지 않음 -> 재시도 가능
                }
            }
        } catch (Exception e) {
            log.error("Error reading from KOSPI stream: {}", e.getMessage(), e);
        }
    }

    /**
     * KOSDAQ 스트림에서 RSI 계산 메시지를 소비합니다.
     */
    @Scheduled(fixedDelay = 1000)
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
                    log.info("KOSDAQ RSI 메세지 - isu_cd: {}, targetDate: {}, marketDate: {}",
                            value.get("isu_cd"),
                            value.get("targetDate"),
                            value.get("marketDate"));

                    // 여기에 KOSDAQ RSI 계산 로직 추가
                    callRSICalculationService(value.get("isu_cd").toString(),
                            value.get("targetDate").toString(),
                            value.get("marketDate").toString());

                    // 처리 성공 시 ACK + 삭제
                    redisTemplate.opsForStream().acknowledge(KOSDAQ_STREAM, CONSUMER_GROUP, message.getId());
                    redisTemplate.opsForStream().delete(KOSDAQ_STREAM, message.getId());
                    log.info("KOSDAQ  메세지 처리 후 삭제: {}", message.getId());

                } catch (Exception e) {
                    log.error("Error processing KOSDAQ message: {}", e.getMessage(), e);
                    // 에러 발생 시 메시지를 삭제하지 않음 -> 재시도 가능
                }
            }
        } catch (Exception e) {
            log.error("Error reading from KOSDAQ stream: {}", e.getMessage(), e);
        }
    }

    /**
     * RSI 계산 서비스를 호출합니다.
     *
     * @param isuCD      종목 코드
     * @param targetDate 대상 날짜
     * @param marketDate 과거 14일 장 날짜 (쉼표 구분)
     */
    private void callRSICalculationService(String isuCD, String targetDate, String marketDate){
        rsiCalculationService.rsiCalculation(isuCD, targetDate, marketDate);
    }
}
