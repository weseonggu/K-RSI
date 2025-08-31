package com.service.RSIranking.consumerTest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;

import static org.assertj.core.api.Assertions.assertThat;
@SpringBootTest
public class BeanCreateTest {
    @Test
    void testBeanExist(ApplicationContext context) {
        assertThat(context.containsBean("streamMessageListenerContainer")).isTrue();
    }

    @Autowired
    private StreamMessageListenerContainer<String, ?> container;

    @Autowired
    private StreamMessageListenerContainer.StreamMessageListenerContainerOptions<String, ?> options;

    @Autowired
    private RedisConnectionFactory connectionFactory;

    @Test
    void printBeanDetails() {
        System.out.println("=== Stream Container ===");
        System.out.println(container);

        System.out.println("=== Options ===");
        System.out.println("pollTimeout: " + options.toString());

        System.out.println("=== Redis Connection Factory ===");
        System.out.println(connectionFactory.getClass().getName());

    }
}
