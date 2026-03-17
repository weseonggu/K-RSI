package com.service.RSIranking.consumer;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.stereotype.Service;

/**
 * Redis Stream Consumer 서비스 (리스너 기반).
 *
 * <p>StreamMessageListenerContainer를 사용하여 Redis Stream의 메시지를
 * 비동기적으로 수신하고 처리합니다.</p>
 *
 * <h2>Consumer Group 구성</h2>
 * <ul>
 *   <li>KOSPI: RSI-Kospi-Group / RSI-Kospi-consumer-01</li>
 *   <li>KOSDAQ: RSI-Kosdaq-Group / RSI-Kosdaq-consumer-01</li>
 * </ul>
 *
 * <h2>활성화 조건</h2>
 * <p>{@code scheduler.rsistreamlistener.enabled=true} 설정 시 활성화됩니다.
 * 기본값은 비활성화이며, RSICalCulationConsumer와 동시에 활성화하지 마십시오.</p>
 *
 * @author RSIranking Team
 * @version 1.1
 */
@Service
@Slf4j
@ConditionalOnProperty(name = "scheduler.rsistreamlistener.enabled", havingValue = "true", matchIfMissing = false)
public class RSIRedisStreamConsumerService {

    private static final String STREAM_KEY_PREFIX = "rsi:calculation:stream:";
    private static final String KOSPI_STREAM = STREAM_KEY_PREFIX + "KOSPI";
    private static final String KOSDAQ_STREAM = STREAM_KEY_PREFIX + "KOSDAQ";
    private static final String KOSPI_CONSUMER_GROUP = "RSI-Kospi-Group";
    private static final String KOSDAQ_CONSUMER_GROUP = "RSI-Kosdaq-Group";
    private static final String KOSPI_CONSUMER_NAME = "RSI-Kospi-consumer-01";
    private static final String KOSDAQ_CONSUMER_NAME = "RSI-Kosdaq-consumer-01";

    private final StreamMessageListenerContainer<String, MapRecord<String, String, String>> listenerContainer;
    private final RSIStreamListener rsiStreamListener;
    private final RedisTemplate<String, Object> redisTemplate;

    public RSIRedisStreamConsumerService(
            @Qualifier("RSIStreamMessageListenerContainer") StreamMessageListenerContainer<String, MapRecord<String, String, String>> streamMessageListenerContainer,
            RSIStreamListener rsiStreamListener,
            @Qualifier("rsiMessageRedisTemplate") RedisTemplate<String, Object> redisTemplate) {

        this.listenerContainer = streamMessageListenerContainer;
        this.rsiStreamListener = rsiStreamListener;
        this.redisTemplate = redisTemplate;
    }

    /**
     * Consumer Group을 초기화하고 리스너를 등록한 뒤 컨테이너를 시작합니다.
     */
    @PostConstruct
    public void initialize() {
        initializeConsumerGroups();
        registerListeners();
        listenerContainer.start();
        log.info("Redis Stream Listener Container 시작 완료");
    }

    private void initializeConsumerGroups() {
        try {
            redisTemplate.opsForStream().createGroup(KOSPI_STREAM, KOSPI_CONSUMER_GROUP);
            log.info("Consumer group created for KOSPI stream");
        } catch (Exception e) {
            log.info("Consumer group already exists for KOSPI stream");
        }

        try {
            redisTemplate.opsForStream().createGroup(KOSDAQ_STREAM, KOSDAQ_CONSUMER_GROUP);
            log.info("Consumer group created for KOSDAQ stream");
        } catch (Exception e) {
            log.info("Consumer group already exists for KOSDAQ stream");
        }
    }

    private void registerListeners() {
        try {
            log.info("코스피 스트림 리스너 등록");
            listenerContainer.receive(
                    Consumer.from(KOSPI_CONSUMER_GROUP, KOSPI_CONSUMER_NAME),
                    StreamOffset.create(KOSPI_STREAM, ReadOffset.lastConsumed()),
                    rsiStreamListener
            );
        } catch (Exception e) {
            log.error("코스피 스트림 리스너 등록 실패: {}", e.getMessage(), e);
        }

        try {
            log.info("코스닥 스트림 리스너 등록");
            listenerContainer.receive(
                    Consumer.from(KOSDAQ_CONSUMER_GROUP, KOSDAQ_CONSUMER_NAME),
                    StreamOffset.create(KOSDAQ_STREAM, ReadOffset.lastConsumed()),
                    rsiStreamListener
            );
        } catch (Exception e) {
            log.error("코스닥 스트림 리스너 등록 실패: {}", e.getMessage(), e);
        }
    }
}
