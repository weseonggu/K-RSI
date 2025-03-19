package com.service.RSIranking.util;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.DayOfWeek;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class IsHoliday {
    private final RestTemplate restTemplate = new RestTemplate();
    private final String SERVICE_KEY = "nsBP4tbtWDH5fmXKGsfPRENsqTy3MpxAZJ0I1B%2FfJy1Wcsiiohpwi5klxFbqzSr5nLhsZIdirTf61OnVSvrgZA%3D%3D";


    /**
     * 어제 날짜가 주말인지 확인
     * @return
     */
    public boolean isWeekend(){
        LocalDate yesterday = LocalDate.now().minusDays(1);
        DayOfWeek dayOfWeek = yesterday.getDayOfWeek();

        return (dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY);

    }

    /**
     * 공공 데이터 포털에서 공휴일 정보 가져오기
     * @param year 년
     * @param month 월
     * @return
     */
    public String getAnniversaryInfo(int year, int month) {
        String baseUrl = "http://apis.data.go.kr/B090041/openapi/service/SpcdeInfoService/getRestDeInfo";


        StringBuilder urlBuilder = new StringBuilder(baseUrl);
        urlBuilder.append("?ServiceKey=").append(SERVICE_KEY)
                .append("&pageNo=1")
                .append("&numOfRows=15")
                .append("&solYear=").append(year)
                .append("&solMonth=").append(String.format("%02d", month));

        String url = urlBuilder.toString();

        // GET 요청 및 응답 처리
        HttpHeaders headers = new HttpHeaders();

        HttpEntity<String> entity = new HttpEntity<>(headers);

        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

        if (response.getStatusCode().is2xxSuccessful()) {
            return response.getBody();
        } else {
            throw new RuntimeException("API 요청 실패: " + response.getStatusCode());
        }
    }

    /**
     * 오늘 날짜가 공휴일 인지 확인
     * @param data
     * @return
     */
    public boolean todayIsHoliday(String data){
        return true;
    }


}
