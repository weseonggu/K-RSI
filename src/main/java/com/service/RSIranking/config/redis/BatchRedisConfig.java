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

/**
 * 배치 작업용 Redis 설정 클래스.
 *
 * <p>배치 Step 간 데이터 공유와 RSI 메시지 발행을 위한 Redis Template을 구성합니다.</p>
 *
 * <h2>제공 Template</h2>
 * <ul>
 *   <li><b>stockRedisTemplate</b>: Step 간 데이터 공유용 (String-String)</li>
 *   <li><b>rsiMessageRedisTemplate</b>: RSI 메시지 발행용 (String-Object)</li>
 * </ul>
 *
 * @author RSIranking Team
 * @version 1.0
 */
@Configuration
public class BatchRedisConfig {

    /**
     * Step 간 데이터 공유용 Redis Template을 생성합니다.
     *
     * <p>Key와 Value 모두 String으로 직렬화합니다.</p>
     *
     * @param connectionFactory Redis 연결 팩토리
     * @return String-String RedisTemplate
     */
    @Bean(name = "stockRedisTemplate")
    public RedisTemplate<String, String> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(RedisSerializer.string());  // Key는 String
        template.setValueSerializer(RedisSerializer.string()); // Value도 String(JSON 저장)
        return template;
    }
    /**
     * RSI 메시지 발행용 Redis Template을 생성합니다.
     *
     * <p>Key는 String, Value는 JSON으로 직렬화합니다.
     * Redis Stream에 메시지를 발행할 때 사용됩니다.</p>
     *
     * @param connectionFactory Redis 연결 팩토리
     * @return String-Object RedisTemplate
     */
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
