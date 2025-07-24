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
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
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
@ConditionalOnProperty(name = "scheduler.rsiproducer.enabled", havingValue = "true", matchIfMissing = false)
public class RSICalculationLauncher {

    private final JobLauncher jobLauncher;
    private final JobRegistry jobRegistry;
    private final KrxApiProperties krxApiProperties;
    private final DateUtil dateUtil;
    private final IsClosedDay isClosedDay;
    private final MarketDayForTheLast14Days marketDayForTheLast14Days;

    @Scheduled(cron = "50 * * * * *", zone = "Asia/Seoul")
    public void RSICalculationSchedule() throws Exception {
        String yesterday = dateUtil.yesterday();
        executeRSICalculation(yesterday);
    }

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

    public void RSICalculationJobLauncher(String targetDate, List<LocalDate> marketDayList) throws Exception {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd-hh-mm-ss");
        String jobExecutionTimestamp = dateFormat.format(new Date());

        String marketDayListString = marketDayList.stream()
                .map(ld -> ld.format(DateTimeFormatter.ofPattern("yyyyMMdd")))
                .collect(Collectors.joining(","));

        String rsiJobName = "RSICalculationJob";

        // KOSPI Job 런처
        JobParameters kospiJobParameters = new JobParametersBuilder()
                .addString("date", jobExecutionTimestamp)
                .addString("targetDate", targetDate)
                .addString("apiUrl", krxApiProperties.getKospiInfoUrl())
                .addString("apiKey", krxApiProperties.getKey())
                .addString("mktNm", "KOSPI")
                .addString("marketDayList", marketDayListString)
                .toJobParameters();

        jobLauncher.run(jobRegistry.getJob(rsiJobName), kospiJobParameters);

        // KOSDAQ Job 런처
        JobParameters kosdaqJobParameters = new JobParametersBuilder()
                .addString("date", jobExecutionTimestamp)
                .addString("targetDate", targetDate)
                .addString("apiUrl", krxApiProperties.getKosdaqInfoUrl())
                .addString("apiKey", krxApiProperties.getKey())
                .addString("mktNm", "KOSDAQ")
                .addString("marketDayList", marketDayListString)
                .toJobParameters();

        jobLauncher.run(jobRegistry.getJob(rsiJobName), kosdaqJobParameters);
    }
}