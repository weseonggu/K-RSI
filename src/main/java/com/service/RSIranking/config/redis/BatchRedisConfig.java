package com.service.RSIranking.config.redis;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.service.RSIranking.dto.RSIMessageDTO;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class BatchRedisConfig {

    @Bean(name = "stockRedisTemplate")
    public RedisTemplate<String, String> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(RedisSerializer.string());  // Key는 String
        template.setValueSerializer(RedisSerializer.string()); // Value도 String(JSON 저장)
        return template;
    }
    @Bean(name = "rsiMessageRedisTemplate")
    public RedisTemplate<String, Object> rsiMessageRedisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.afterPropertiesSet();
        return template;
    }

//    @Bean(name = "rsiMessageRedisTemplate")
//    public RedisTemplate<String, RSIMessageDTO> rsiMessageRedisTemplate(RedisConnectionFactory connectionFactory) {
//        RedisTemplate<String, RSIMessageDTO> template = new RedisTemplate<>();
//        template.setConnectionFactory(connectionFactory);
//
//        // Key는 String
//        template.setKeySerializer(new StringRedisSerializer());
//
//        // Value는 DTO → JSON (GenericJackson2JsonRedisSerializer 사용)
//        ObjectMapper mapper = new ObjectMapper()
//                .setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
//
//        GenericJackson2JsonRedisSerializer serializer = new GenericJackson2JsonRedisSerializer(mapper);
//
//        template.setValueSerializer(serializer);
//        template.setHashKeySerializer(new StringRedisSerializer());
//        template.setHashValueSerializer(serializer);
//
//        template.afterPropertiesSet();
//        return template;
//    }
//    @Bean(name = "rsiMessageRedisTemplate")
//    public RedisTemplate<String, RSIMessageDTO> rsiMessageRedisTemplate(RedisConnectionFactory connectionFactory) {
//        RedisTemplate<String, RSIMessageDTO> template = new RedisTemplate<>();
//        template.setConnectionFactory(connectionFactory);
//
//        template.setKeySerializer(new StringRedisSerializer());
//        template.setValueSerializer(new Jackson2JsonRedisSerializer<>(RSIMessageDTO.class));
//        template.setHashKeySerializer(new StringRedisSerializer());
//        template.setHashValueSerializer(new Jackson2JsonRedisSerializer<>(RSIMessageDTO.class));
//
//        template.afterPropertiesSet();
//        return template;
//    }
}
