package com.service.RSIranking;

import com.service.RSIranking.config.krx_api.ApiConfig;
import com.service.RSIranking.config.krx_api.KrxApiProperties;
import com.service.RSIranking.service.KrxRequestService;
import com.service.RSIranking.util.IsClosedDay;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
@SpringBootTest
@ActiveProfiles("test")
public class IsClosedDayTest {

    @Autowired
    private IsClosedDay isClosedDay;

    @Test
    public void setIsClosedDayTestTrue(){
        boolean isClosed = isClosedDay.isClosedDay("20250525");
        assertEquals(true, isClosed);
    }

    @Test
    public void setIsClosedDayTestFalse(){
        boolean isClosed = isClosedDay.isClosedDay("20250523");
        assertEquals(false, isClosed);
    }

    @Test
    public void testFindMarketDays() {
        List<Integer> openMarketDays = new ArrayList<>();
        LocalDate date = LocalDate.now().minusDays(1); // 어제부터 시작

        while (openMarketDays.size() < 100) {
            int formattedDate = Integer.parseInt(date.format(DateTimeFormatter.ofPattern("yyyyMMdd")));
            boolean isClosed = isClosedDay.isClosedDay(String.valueOf(formattedDate));

            if (!isClosed) {
                openMarketDays.add(formattedDate);
            }

            date = date.minusDays(1);
        }

        // 출력
        System.out.println("최근 100일간의 거래일:");
        for (Integer marketDay : openMarketDays) {
            System.out.println(marketDay+",");
        }
    }

    @Autowired
    private KrxRequestService krxRequestService;
    @Autowired
    private KrxApiProperties krxApiProperties;
    @Test
    @DisplayName("krx요청 테스트")
    public void krxRequestTest(){
        ApiConfig apiConfig = new ApiConfig(krxApiProperties.getKey(), krxApiProperties.getKospiInfoUrl());
        ResponseEntity<Map> response = krxRequestService.krxRequest(apiConfig, "20250709");
        System.out.println(response.getBody());
    }

}
