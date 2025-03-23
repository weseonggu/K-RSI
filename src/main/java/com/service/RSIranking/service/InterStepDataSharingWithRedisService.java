package com.service.RSIranking.service;

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

@Service
@Slf4j
public class InterStepDataSharingWithRedisService {
    private final RedisTemplate<String, List<StockDto>> redisTemplate;

    public InterStepDataSharingWithRedisService (
            @Qualifier("stockRedisTemplate")RedisTemplate<String, List<StockDto>> stockRedisTemplate
    ){
        this.redisTemplate = stockRedisTemplate;
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
    public boolean putStockToRedis(String key, List<StockDto> value){
        try {
            ValueOperations<String, List<StockDto>> ops = redisTemplate.opsForValue();
            ops.set(key, value, Duration.ofHours(3));
            return true;
        }catch (Exception e){
            log.error("Redis 저장 중 예외 발생: ", e);
            throw new RuntimeException("레디스 저장 실패");
        }

    }

    @Recover
    public boolean failToPutData(RuntimeException e, String key, List<StockDto> value){
        log.info("Redis에 데이터 저장 실패: "+e);
        return false;
    }

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
    public List<StockDto> getStockToRedis(String key){
        try{
            ValueOperations<String, List<StockDto>> ops = redisTemplate.opsForValue();
            return ops.get(key);
        }catch (Exception e){
            log.error("Redis에서 조회 중 예외 발생: ", e);
            throw new RuntimeException("레디스 조회 실패");
        }
    }
    @Recover
    public boolean failToGetData(RuntimeException e, String key){
        log.info("Redis에 데이터 조회 실패: "+e);
        return false;
    }
}
