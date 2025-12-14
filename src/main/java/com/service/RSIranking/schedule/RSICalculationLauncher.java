package com.service.RSIranking.schedule;

import com.service.RSIranking.config.krx_api.KrxApiProperties;
import com.service.RSIranking.util.DateUtil;
import com.service.RSIranking.util.IsClosedDay;
import com.service.RSIranking.util.MarketDayForTheLast14Days;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.configuration.JobRegistry;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RSICalculationLauncher {

    private final JobLauncher jobLauncher;
    private final JobRegistry jobRegistry;
    private final KrxApiProperties krxApiProperties;
    private final DateUtil dateUtil;
    private final IsClosedDay isClosedDay;
    private final MarketDayForTheLast14Days marketDayForTheLast14Days;

//    @Scheduled(cron = "5 * * * * *", zone = "Asia/Seoul")
    public void RSICalculationSchedule() throws Exception {
        String yesterday = dateUtil.yesterday();
        executeRSICalculation(yesterday);
    }

//    @Scheduled(cron = "5 * * * * *", zone = "Asia/Seoul")
    public void executeRSICalculation(String date) throws Exception {

        String yesterday = date;
        if(dateUtil.isWeekend(yesterday)){
            log.info("주말 입니다. RSI 지표 계산 배치를 실행하지 않습니다.");
            return;
        }
        boolean isClosed = isClosedDay.isClosedDay(yesterday);
        if(!isClosed){
            log.info("RSI 지표 계산 업데이트 시작");
            // 날짜 구하기 리스트
            List<LocalDate> marketDay = marketDayForTheLast14Days.getMarketDayForTheLast14Days(yesterday);
            RSICalculationJobLauncher(yesterday, marketDay);

        }else {
            log.info("휴장일 RSI 지표 계산 업데이트 없음");
        }
    }
