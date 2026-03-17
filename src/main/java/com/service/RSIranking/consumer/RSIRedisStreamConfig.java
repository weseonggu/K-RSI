package com.service.RSIranking.consumer;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;

import java.time.Duration;

/**
 * RSI Redis Stream 설정 클래스.
 *
 * <p>Redis Stream에서 메시지를 수신하기 위한 StreamMessageListenerContainer를 구성합니다.</p>
 *
 * <h2>컨테이너 설정</h2>
 * <ul>
 *   <li>Poll Timeout: 1초</li>
 *   <li>메시지 타입: MapRecord (String-String-String)</li>
 * </ul>
 *
 * @author RSIranking Team
 * @version 1.1
 */
@Configuration
public class RSIRedisStreamConfig {

    /**
     * RSI 지표 계산 전용 Redis Stream 컨테이너 빈.
     *
     * <p>MapRecord 형태로 메시지를 수신하며, pollTimeout은 1초입니다.</p>
     *
     * @param connectionFactory Redis 연결 팩토리
     * @return StreamMessageListenerContainer
     */
    @Bean(name = "RSIStreamMessageListenerContainer")
    public StreamMessageListenerContainer<String, MapRecord<String, String, String>> streamMessageListenerContainer(
            RedisConnectionFactory connectionFactory) {

        StreamMessageListenerContainer.StreamMessageListenerContainerOptions<String, MapRecord<String, String, String>> options =
                StreamMessageListenerContainer.StreamMessageListenerContainerOptions
                        .<String, MapRecord<String, String, String>>builder()
                        .pollTimeout(Duration.ofSeconds(1))
                        .build();

        return StreamMessageListenerContainer.create(connectionFactory, options);
    }
}
