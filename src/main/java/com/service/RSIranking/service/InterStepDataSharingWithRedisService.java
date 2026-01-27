package com.service.RSIranking.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.service.RSIranking.dto.StockDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * 배치 Step 간 데이터 공유를 위한 Redis 서비스.
 *
 * <p>Spring Batch의 Step 간에 대량의 데이터를 공유하기 위해 Redis를 활용합니다.
 * ExecutionContext의 크기 제한을 우회하고, Step 간 데이터 전달을 효율적으로 처리합니다.</p>
 *
 * <h2>주요 기능</h2>
 * <ul>
 *   <li>Step 간 데이터 Redis 저장 (3시간 TTL)</li>
 *   <li>Step 간 데이터 Redis 조회</li>
 *   <li>저장/조회 실패 시 재시도 (최대 3회)</li>
 * </ul>
 *
 * <h2>데이터 직렬화</h2>
 * <p>Jackson ObjectMapper를 사용하여 JSON 형태로 직렬화/역직렬화합니다.</p>
 *
 * @author RSIranking Team
 * @version 1.0
 */
@Service
@Slf4j
public class InterStepDataSharingWithRedisService {
    private final RedisTemplate<String, String > stockRedisTemplate;
    private final ObjectMapper objectMapper;

    public InterStepDataSharingWithRedisService (
            @Qualifier("stockRedisTemplate")RedisTemplate<String, String> stockRedisTemplate,
            ObjectMapper objectMapper
    ){
        this.stockRedisTemplate = stockRedisTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 스탭간 데이터 공유를 위한 레디스 저장
     * @param key 키
     * @param value 데이터
     * @return 성공 여부 0 1
     */
    @Retryable(recover = "failToPutData",
            retryFor = {
            RuntimeException.class
    }
    , maxAttempts = 3, backoff = @Backoff(delay = 2000))
    public <T> boolean putStockToRedis(String key, T value){
        try {
            String jsonData = objectMapper.writeValueAsString(value);

            ValueOperations<String, String> ops = stockRedisTemplate.opsForValue();
            ops.set(key, jsonData, Duration.ofHours(3));
            return true;
        }catch (Exception e){
            log.error("Redis 저장 중 예외 발생: ", e);
            throw new RuntimeException("레디스 저장 실패");
        }

    }
//    public boolean putStockToRedis(String key, List<StockDto> value){
//        try {
//            ValueOperations<String, List<StockDto>> ops = stockRedisTemplate.opsForValue();
//            ops.set(key, value, Duration.ofHours(3));
//            return true;
//        }catch (Exception e){
//            log.error("Redis 저장 중 예외 발생: ", e);
//            throw new RuntimeException("레디스 저장 실패");
//        }
//
//    }

    @Recover
    public boolean failToPutData(RuntimeException e, String key, List<StockDto> value){
        log.info("Redis에 데이터 저장 실패: "+e);
        return false;
    }

//=============================================================================================================

    /**
     * 스탭간 데이터 고유시 데이터 조회 
     * @param key 키
     * @return 데이터
     */
    @Retryable(recover = "failToGetData",
            retryFor = {
                    RuntimeException.class
            }
            , maxAttempts = 3, backoff = @Backoff(delay = 2000))
    public <T> Optional<T> getStockToRedis(String key, TypeReference<T> typeReference){
        try{
            String jsonData = stockRedisTemplate.opsForValue().get(key);
            if (jsonData != null) {
                T data  = objectMapper.readValue(jsonData, typeReference);
                return Optional.ofNullable(data);
            }
            return null;
        }catch (Exception e){
            log.error("Redis에서 조회 중 예외 발생: ", e);
            throw new RuntimeException("레디스 조회 실패");
        }
    }
//    public List<StockDto> getStockToRedis(String key){
//        try{
//            ValueOperations<String, List<StockDto>> ops = stockRedisTemplate.opsForValue();
//            return ops.get(key);
//        }catch (Exception e){
//            log.error("Redis에서 조회 중 예외 발생: ", e);
//            throw new RuntimeException("레디스 조회 실패");
//        }
//    }
    @Recover
    public boolean failToGetData(RuntimeException e, String key){
        log.info("Redis에 데이터 조회 실패: "+e);
        return false;
    }

}
