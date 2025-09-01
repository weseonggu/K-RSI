package com.service.RSIranking.consumer;

import com.service.RSIranking.dto.RSIMessageDTO;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;

import java.time.Duration;

@Configuration
public class RSIResdisStreamConfig {

//    @Bean(name = "RSIStreamMessageListenerContainer")
//    public StreamMessageListenerContainer<String, ?> streamMessageListenerContainer(RedisConnectionFactory connectionFactory) {
//        StreamMessageListenerContainer.StreamMessageListenerContainerOptions<String, ?> options =
//                StreamMessageListenerContainer.StreamMessageListenerContainerOptions.builder()
//                        .pollTimeout(Duration.ofSeconds(1))  // polling 주기
//                        .build();
//
//        return StreamMessageListenerContainer.create(connectionFactory, options);
//    }

    /**
     * 레디스 스트림 컨테이너: RSI지표 계산 전용 스트림 컨테이너 빈
     *
     * 이 Bean은 Redis Stream에서 메시지를 읽어오는 컨슈머 컨테이너입니다.
     * - Key 타입: String
     * - Value 타입: ObjectRecord<String, RSIMessageDTO>
     * - targetType(RSIMessageDTO.class) 로 지정하여, Redis에 저장된 메시지를 RSIMessageDTO로 역직렬화함
     * - pollTimeout(Duration.ofSeconds(1)): Redis에서 메시지를 가져올 때 블로킹 대기할 최대 시간
     *
     * 컨슈머 로직(StreamListener 구현체)은 이 컨테이너에 등록해서 메시지를 처리하게 됩니다.
     */
    @Bean(name = "RSIStreamMessageListenerContainer")
    public StreamMessageListenerContainer<String, ObjectRecord<String, RSIMessageDTO>> streamMessageListenerContainer(RedisConnectionFactory connectionFactory) {
        StreamMessageListenerContainer.StreamMessageListenerContainerOptions<String, ObjectRecord<String, RSIMessageDTO>> options =
                StreamMessageListenerContainer.StreamMessageListenerContainerOptions
                        .<String, ObjectRecord<String, RSIMessageDTO>>builder()
                        .pollTimeout(Duration.ofSeconds(1))  // polling 주기
                        .targetType(RSIMessageDTO.class)
                        .build();

        return StreamMessageListenerContainer.create(connectionFactory, options);
    }


}
