package com.service.RSIranking.consumer;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
 * (폴링 방식 RSICalCulationConsumer는 제거되어 본 리스너가 유일한 소비자입니다)</p>
 *
 * @author RSIranking Team
 * @version 1.2
 */
@Service
@Slf4j
@ConditionalOnProperty(name = "scheduler.rsistreamlistener.enabled", havingValue = "true", matchIfMissing = false)
public class RSIRedisStreamConsumerService {

    private static final String STREAM_KEY_PREFIX = "rsi:calculation:stream:";
    private static final String KOSPI_STREAM = STREAM_KEY_PREFIX + "KOSPI";
    private static final String KOSDAQ_STREAM = STREAM_KEY_PREFIX + "KOSDAQ";
    private static final String ETF_STREAM = STREAM_KEY_PREFIX + "ETF";
    private static final String KOSPI_CONSUMER_GROUP = "RSI-Kospi-Group";
    private static final String KOSDAQ_CONSUMER_GROUP = "RSI-Kosdaq-Group";
    private static final String ETF_CONSUMER_GROUP = "RSI-Etf-Group";
    private static final String KOSPI_CONSUMER_NAME = "RSI-Kospi-consumer-01";
    private static final String KOSDAQ_CONSUMER_NAME = "RSI-Kosdaq-consumer-01";
    private static final String ETF_CONSUMER_NAME = "RSI-Etf-consumer-01";

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
     * Consumer Group을 초기화하고, 이전 실행에서 ACK되지 못한 pending 메시지를 재처리한 뒤
     * 리스너를 등록하고 컨테이너를 시작합니다.
     */
    @PostConstruct
    public void initialize() {
        initializeConsumerGroups();
        reprocessPendingMessages(KOSPI_STREAM, KOSPI_CONSUMER_GROUP, KOSPI_CONSUMER_NAME);
        reprocessPendingMessages(KOSDAQ_STREAM, KOSDAQ_CONSUMER_GROUP, KOSDAQ_CONSUMER_NAME);
        reprocessPendingMessages(ETF_STREAM, ETF_CONSUMER_GROUP, ETF_CONSUMER_NAME);
        registerListeners();
        listenerContainer.start();
        log.info("Redis Stream Listener Container 시작 완료");
    }

    /**
     * 이 consumer의 PEL(Pending Entries List)에 남아 있는 메시지를 재처리합니다.
     *
     * <p>리스너는 {@code ReadOffset.lastConsumed()}('>')로만 읽기 때문에, 처리 도중
     * 애플리케이션이 종료되면 전달만 되고 ACK되지 못한 메시지가 PEL에 영구히 남는다.
     * (실제로 catch-up 러너 중단으로 KOSDAQ 스트림에 1,200여 건이 방치된 사례 존재)
     * XREADGROUP에 명시적 ID를 주면 해당 consumer의 pending 메시지가 반환되므로,
     * 기동 시 한 번 전체를 순회하며 리스너와 동일한 로직으로 재처리한다.
     * 성공한 메시지는 리스너 내부에서 ACK/XDEL 되고, 실패한 메시지는 PEL에 남아
     * 다음 기동 시 다시 시도된다.</p>
     */
    private void reprocessPendingMessages(String streamKey, String groupName, String consumerName) {
        String offset = "0";
        int reprocessed = 0;
        try {
            while (true) {
                List<MapRecord<String, Object, Object>> records = redisTemplate.opsForStream().read(
                        Consumer.from(groupName, consumerName),
                        StreamReadOptions.empty().count(100),
                        StreamOffset.create(streamKey, ReadOffset.from(offset)));
                if (records == null || records.isEmpty()) {
                    break;
                }
                for (MapRecord<String, Object, Object> record : records) {
                    rsiStreamListener.onMessage(toStringRecord(record));
                    reprocessed++;
                }
                // 같은 메시지를 무한 반복하지 않도록 마지막으로 읽은 ID 이후부터 이어서 읽는다
                offset = records.get(records.size() - 1).getId().getValue();
            }
        } catch (Exception e) {
            // pending 재처리 실패가 신규 메시지 소비까지 막지는 않도록 경고만 남긴다
            log.warn("Pending 메시지 재처리 중 오류 - stream: {}, group: {}, 처리 완료: {}건",
                    streamKey, groupName, reprocessed, e);
            return;
        }
        if (reprocessed > 0) {
            log.info("Pending 메시지 재처리 완료 - stream: {}, group: {}, {}건", streamKey, groupName, reprocessed);
        }
    }

    /**
     * rsiMessageRedisTemplate은 hash key/value를 String으로 직렬화하므로
     * Object 타입 레코드를 String 레코드로 변환해 리스너에 전달합니다.
     */
    private static MapRecord<String, String, String> toStringRecord(MapRecord<String, Object, Object> record) {
        Map<String, String> value = new LinkedHashMap<>();
        record.getValue().forEach((k, v) -> value.put(String.valueOf(k), String.valueOf(v)));
        return MapRecord.create(record.getStream(), value).withId(record.getId());
    }

    private void initializeConsumerGroups() {
        createGroupIfAbsent(KOSPI_STREAM, KOSPI_CONSUMER_GROUP);
        createGroupIfAbsent(KOSDAQ_STREAM, KOSDAQ_CONSUMER_GROUP);
        createGroupIfAbsent(ETF_STREAM, ETF_CONSUMER_GROUP);
    }

    /**
     * Consumer group을 생성합니다. 이미 존재하는 경우(BUSYGROUP)만 정상으로 간주하고,
     * 그 외 오류(Redis 연결 실패 등)는 기동 실패로 전파합니다.
     * 과거에는 모든 예외를 "already exists"로 삼켜서 연결 장애가 은폐되었습니다.
     */
    private void createGroupIfAbsent(String streamKey, String groupName) {
        try {
            redisTemplate.opsForStream().createGroup(streamKey, groupName);
            log.info("Consumer group 생성 - stream: {}, group: {}", streamKey, groupName);
        } catch (Exception e) {
            if (isBusyGroup(e)) {
                log.info("Consumer group 이미 존재 - stream: {}, group: {}", streamKey, groupName);
            } else {
                throw new IllegalStateException(
                        "Consumer group 생성 실패 - stream: " + streamKey + ", group: " + groupName, e);
            }
        }
    }

    private static boolean isBusyGroup(Throwable e) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            String message = cause.getMessage();
            if (message != null && message.contains("BUSYGROUP")) {
                return true;
            }
        }
        return false;
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

        try {
            log.info("ETF 스트림 리스너 등록");
            listenerContainer.receive(
                    Consumer.from(ETF_CONSUMER_GROUP, ETF_CONSUMER_NAME),
                    StreamOffset.create(ETF_STREAM, ReadOffset.lastConsumed()),
                    rsiStreamListener
            );
        } catch (Exception e) {
            log.error("ETF 스트림 리스너 등록 실패: {}", e.getMessage(), e);
        }
    }
}
