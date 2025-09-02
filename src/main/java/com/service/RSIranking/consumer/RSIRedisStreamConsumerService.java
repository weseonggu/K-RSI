package com.service.RSIranking.consumer;

import com.service.RSIranking.dto.RSIMessageDTO;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.stereotype.Service;

@Service
public class RSIRedisStreamConsumerService {


    private static final String STREAM_KEY_PREFIX = "rsi:calculation:stream:";
    private static final String KOSPI_STREAM = STREAM_KEY_PREFIX + "KOSPI";
    private static final String KOSDAQ_STREAM = STREAM_KEY_PREFIX + "KOSDAQ";
    private final StreamMessageListenerContainer<String, ObjectRecord<String, RSIMessageDTO>> listenerContainer;
    private final RSIStreanListener rsiStreanListener;

    public RSIRedisStreamConsumerService (
            @Qualifier("RSIStreamMessageListenerContainer")StreamMessageListenerContainer streamMessageListenerContainer,
            RSIStreanListener rsiStreanListener){

        this.listenerContainer = streamMessageListenerContainer;
        this.rsiStreanListener = rsiStreanListener;

    }
    @PostConstruct
    public void startKospi() {
        listenerContainer.receive(
                Consumer.from("RSI-Kospi-Group", "RSI-Kospi-consumer-01"),  // consumer group 설정
                StreamOffset.create(KOSPI_STREAM, ReadOffset.lastConsumed()),  // 스트림과 오프셋 지정
                rsiStreanListener
        );

        listenerContainer.start();
    }

    @PostConstruct
    public void startKosdaq() {
        listenerContainer.receive(
                Consumer.from("RSI-Kosdaq-Group", "RSI-Kosdaq-consumer-01"),  // consumer group 설정
                StreamOffset.create(KOSDAQ_STREAM, ReadOffset.lastConsumed()),  // 스트림과 오프셋 지정
                rsiStreanListener
        );

        listenerContainer.start();
    }
}
