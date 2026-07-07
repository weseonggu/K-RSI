package com.service.RSIranking.util;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 최근 14일간 영업일 조회 서비스.
 *
 * <p>RSI 지표 계산을 위해 주어진 날짜로부터 과거 13일간의
 * 주식 시장 영업일 목록을 조회합니다.</p>
 *
 * <p>RSI 계산에는 14일간의 가격 데이터가 필요하며,
 * 이 서비스는 기준일을 제외한 과거 13일의 영업일을 반환합니다.</p>
 *
 * <p>영업일 판단 기준:</p>
 * <ul>
 *   <li>주말(토요일, 일요일) 제외</li>
 *   <li>휴장일(공휴일 등) 제외</li>
 * </ul>
 *
 * @author RSIranking Team
 * @version 1.0
 * @see IsClosedDay
 * @see DateUtil
 */
@Service
@RequiredArgsConstructor
public class MarketDayForTheLast14Days {

    private final IsClosedDay isClosedDay;
    private final DateUtil dateUtil;

    /**
     * 주어진 날짜로부터 과거 13일간의 영업일 목록을 조회합니다.
     *
     * <p>기준일로부터 하루씩 과거로 이동하며 영업일을 찾습니다.
     * 주말과 휴장일을 제외하고 13개의 영업일이 수집될 때까지 반복합니다.</p>
     *
     * <p>RSI 계산 시 기준일의 데이터와 함께 사용되어
     * 총 14일간의 가격 변동 데이터를 구성합니다.</p>
     *
     * @param batchDate 기준 날짜 (yyyyMMdd 형식)
     * @return 과거 13일간의 영업일 목록 (LocalDate)
     */
    public List<LocalDate> getMarketDayForTheLast14Days(String batchDate){
        List<LocalDate> marketDays = new ArrayList<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd");
        LocalDate currentDate = LocalDate.parse(batchDate, formatter);
        
        while (marketDays.size() < 13) {
            currentDate = currentDate.minusDays(1);
            String dateStr = currentDate.format(formatter);
            
            // 주말이 아닌 경우에만 휴장일 체크
            if (!dateUtil.isWeekend(dateStr)) {
                // 휴장일이 아닌 경우에만 리스트에 추가
                if (!isClosedDay.isClosedDay(dateStr)) {
                    marketDays.add(currentDate);
                }
            }
        }
        
        return marketDays;
    }

}
