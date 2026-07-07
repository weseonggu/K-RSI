package com.service.RSIranking;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.service.RSIranking.entity.KospiDailyTradingInformation;
import com.service.RSIranking.repository.jdbc.DailyTradingInformationJDBCRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.util.List;

@SpringBootTest
public class TradingDataSelect {

    @Autowired
    private DailyTradingInformationJDBCRepository dailyTradingInformationJDBCRepository;

    @Test
    public void selectTest(){
        List<LocalDate> dates = List.of(
                LocalDate.of(2025, 5, 14),
                LocalDate.of(2025, 5, 13),
                LocalDate.of(2025, 5, 12),
                LocalDate.of(2025, 5, 9),
                LocalDate.of(2025, 5, 8),
                LocalDate.of(2025, 5, 7),
                LocalDate.of(2025, 5, 2),
                LocalDate.of(2025, 4, 30),
                LocalDate.of(2025, 4, 29),
                LocalDate.of(2025, 4, 28),
                LocalDate.of(2025, 4, 25),
                LocalDate.of(2025, 4, 24),
                LocalDate.of(2025, 4, 23),
                LocalDate.of(2025, 4, 22)
        );
        ObjectMapper mapper = new ObjectMapper();
        List<KospiDailyTradingInformation> infoList = dailyTradingInformationJDBCRepository.findByIsuCdAndDateIn("000020", dates, "KOSPI");
        for (KospiDailyTradingInformation data : infoList){
            System.out.println(data.getId()+" "+data.getCmpprevddPrc()+" "+data.getFlucRt());
        }
    }

}
