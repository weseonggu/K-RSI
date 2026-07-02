package com.service.RSIranking.util;

import com.service.RSIranking.config.krx_api.ApiConfig;
import com.service.RSIranking.config.krx_api.KrxApiProperties;
import com.service.RSIranking.service.KrxRequestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 주식 시장 휴장일 확인 서비스.
 *
 * <p>KRX API를 호출하여 특정 날짜가 주식 시장 휴장일인지 확인합니다.
 * API 호출 실패 시 최대 3회까지 재시도합니다.</p>
 *
 * <p>휴장일 판단 기준:</p>
 * <ul>
 *   <li>KRX API 응답에 종목 데이터가 없으면 휴장일로 판단</li>
 *   <li>API 호출 실패 시 복구 메서드를 통해 false 반환</li>
 * </ul>
 *
 * @author RSIranking Team
 * @version 1.0
 * @see com.service.RSIranking.service.KrxRequestService
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IsClosedDay {

    private final KrxRequestService krxRequestService;
    private final KrxApiProperties krxApiProperties;


    /**
     * 주어진 날짜가 주식 시장 휴장일인지 확인합니다.
     *
     * <p>KRX API를 호출하여 해당 날짜의 종목 정보를 조회합니다.
     * 응답에 종목 데이터가 없으면 휴장일로 판단합니다.</p>
     *
     * <p>API 호출 실패 시 최대 3회까지 2초 간격으로 재시도하며,
     * 모든 재시도 실패 시 {@link #failToGetAPI} 메서드가 호출됩니다.</p>
     *
     * @param date 확인할 날짜 (yyyyMMdd 형식)
     * @return 휴장일이면 true, 영업일이면 false
     * @throws RuntimeException KRX API 호출 실패 시 (재시도 대상)
     */
    @Retryable(recover = "failToGetAPI",
            retryFor = {
                    RuntimeException.class
            }
            , maxAttempts = 3, backoff = @Backoff(delay = 2000))
    public boolean isClosedDay(String date){

        ApiConfig apiConfig = new ApiConfig(krxApiProperties.getKey(), krxApiProperties.getKospiInfoUrl());
        ResponseEntity<Map> response = krxRequestService.krxRequest(apiConfig, date);

        // null 체크를 먼저 수행해야 NPE 발생 안 함
        if (response == null || response.getStatusCode() != HttpStatus.OK || response.getBody() == null ||
                !response.getBody().containsKey("OutBlock_1")){
            throw new RuntimeException("KRX API에 문제가 생김 요청 실패");
        }

        List<Map<String, Object>> stockList = (List<Map<String, Object>>) response.getBody().get("OutBlock_1");

        if(stockList.isEmpty()){
            return true;
        }else{
            return false;
        }
    }

    /**
     * KRX API 호출 실패 시 복구 메서드.
     *
     * <p>최대 재시도 횟수 초과 시 호출됩니다. 과거에는 false(영업일)를 반환했으나,
     * 그 경우 KRX 장애일에 배치가 강행되어 잘못된 날짜 기준으로 RSI가 계산되므로
     * 예외를 던져 호출자가 배치를 중단하도록 합니다.</p>
     *
     * @param e 발생한 예외
     * @param date 조회 대상 날짜
     * @throws IllegalStateException 항상 발생 (휴장일 판단 불가)
     */
    @Recover
    public boolean failToGetAPI(RuntimeException e, String date){
        // todo KRX API에 문제가 생김 알림 생성 필요
        log.error("휴장일 확인용 KRX API 호출이 재시도 후에도 실패 - 날짜: {}, 원인: {}", date, e.getMessage(), e);
        throw new IllegalStateException("KRX API로 휴장일 여부를 확인할 수 없어 배치를 진행할 수 없습니다. 날짜: " + date, e);
    }

}
