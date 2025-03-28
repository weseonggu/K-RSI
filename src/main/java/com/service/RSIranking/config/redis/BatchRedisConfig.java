package com.service.RSIranking.config.redis;

import com.service.RSIranking.dto.TradingInfoDto;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;

import java.util.List;

@Configuration
public class BatchRedisConfig {
//    @Bean(name = "stockRedisTemplate")
//    public RedisTemplate<String, List<StockDto>> stockRedisTemplate(
//            RedisConnectionFactory connectionFactory
//    ){
//        RedisTemplate<String, List<StockDto>> template = new RedisTemplate<>();
//        template.setConnectionFactory(connectionFactory);
//        template.setKeySerializer(RedisSerializer.string());
//        template.setValueSerializer(RedisSerializer.json());
//        return template;
//    }

    @Bean(name = "requestDailyTradingInfo")
    public RedisTemplate<String, List<TradingInfoDto>> tradingRedisTemplate(
            RedisConnectionFactory connectionFactory
    ){
        RedisTemplate<String, List<TradingInfoDto>> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(RedisSerializer.string());
        template.setValueSerializer(RedisSerializer.json());
        return template;
    }

    @Bean(name = "stockRedisTemplate")
    public RedisTemplate<String, String> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(RedisSerializer.string());  // Key는 String
        template.setValueSerializer(RedisSerializer.string()); // Value도 String(JSON 저장)
        return template;
    }
}
