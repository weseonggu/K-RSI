package com.service.RSIranking.schedule;

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

@Service
@RequiredArgsConstructor
@Slf4j
public class IsClosedDay {

    private final KrxRequestService krxRequestService;
    private final KrxApiProperties krxApiProperties;


    /**
     * 배치를 수행하는 날짜에 주식 시장이 휴장인지 확인하는 메서드
     * @param date 휴장일인지 알고자하는 날짜
     * @return T/F
     */
    @Retryable(recover = "failToGetAPI",
            retryFor = {
                    RuntimeException.class
            }
            , maxAttempts = 3, backoff = @Backoff(delay = 2000))
    public boolean isClosedDay(String date){

        ApiConfig apiConfig = new ApiConfig(krxApiProperties.getKey(), krxApiProperties.getKospiInfoUrl());
        ResponseEntity<Map> response = krxRequestService.krxRequest(apiConfig, date);

        if (response.getStatusCode() != HttpStatus.OK || response.getBody() == null || response == null ||
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

    @Recover
    public boolean failToGetAPI(RuntimeException e, String date){
        // todo KRX API에 문제가 생김 알림 생성 필요
        log.info(e.getMessage() + "-> 날짜: " + date);
        return false;
    }

}
