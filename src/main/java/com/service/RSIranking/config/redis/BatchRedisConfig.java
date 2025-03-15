package com.service.RSIranking.config.redis;

import com.service.RSIranking.dto.KospiSecuritiesStockDto;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;

import java.util.List;

@Configuration
public class BatchRedisConfig {
    @Bean(name = "stockRedisTemplate")
    public RedisTemplate<String, List<KospiSecuritiesStockDto>> stockRedisTemplate(
            RedisConnectionFactory connectionFactory
    ){
        RedisTemplate<String, List<KospiSecuritiesStockDto>> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(RedisSerializer.string());
        template.setValueSerializer(RedisSerializer.json());
        return template;
    }
}
