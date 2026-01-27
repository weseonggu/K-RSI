package com.service.RSIranking.consumer;

import com.service.RSIranking.dto.RSIMessageDTO;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.stereotype.Service;

/**
 * Redis Stream Consumer 서비스.
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
 * @author RSIranking Team
 * @version 1.0
 */
@Service
@Slf4j
public class RSIRedisStreamConsumerService {


    private static final String STREAM_KEY_PREFIX = "rsi:calculation:stream:";
    private static final String KOSPI_STREAM = STREAM_KEY_PREFIX + "KOSPI";
    private static final String KOSDAQ_STREAM = STREAM_KEY_PREFIX + "KOSDAQ";
    private static final String KOSPI_CONSUMER_GROUP = "RSI-Kospi-Group";
    private static final String KOSDAQ_CONSUMER_GROUP = "RSI-Kosdaq-Group";
    private static final String KOSPI_CONSUMER_NAME = "RSI-Kospi-consumer-";
    private static final String KOSDAQ_CONSUMER_NAME = "RSI-Kosdaq-consumer-";
    private final StreamMessageListenerContainer<String, ObjectRecord<String, RSIMessageDTO>> listenerContainer;
    private final RSIStreanListener rsiStreanListener;
    private final RedisTemplate<String, RSIMessageDTO> redisTemplate;

    public RSIRedisStreamConsumerService (
            @Qualifier("RSIStreamMessageListenerContainer")StreamMessageListenerContainer streamMessageListenerContainer,
            RSIStreanListener rsiStreanListener,
            @Qualifier("rsiMessageRedisTemplate") RedisTemplate redisTemplate){

        this.listenerContainer = streamMessageListenerContainer;
        this.rsiStreanListener = rsiStreanListener;
        this.redisTemplate = redisTemplate;
        initializeConsumerGroups();
    }

    /**
     * Consumer Group을 초기화합니다.
     */
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

    /**
     * KOSPI 스트림 Consumer를 시작합니다.
     */
    @PostConstruct
    public void startKospi() {
        try{
            log.info("코스피 메세지 가져오기");
            listenerContainer.receive(
                    Consumer.from(KOSPI_CONSUMER_GROUP, KOSPI_CONSUMER_NAME+"01"),  // consumer group 설정
                    StreamOffset.create(KOSPI_STREAM, ReadOffset.from("0")),  // 스트림과 오프셋 지정
                    rsiStreanListener
            );

            listenerContainer.start();
        }catch (Exception e){
            log.info(e.getMessage());
        }

    }

    /**
     * KOSDAQ 스트림 Consumer를 시작합니다.
     */
    @PostConstruct
    public void startKosdaq() {
        try{
            log.info("코스닥 메세지 가져오기");
            listenerContainer.receive(
                    Consumer.from(KOSDAQ_CONSUMER_GROUP, KOSDAQ_CONSUMER_NAME+"01"),  // consumer group 설정
                    StreamOffset.create(KOSDAQ_STREAM, ReadOffset.from("0")),  // 스트림과 오프셋 지정
                    rsiStreanListener
            );

            listenerContainer.start();
        }catch (Exception e){
            log.info(e.getMessage());
        }
    }
}
