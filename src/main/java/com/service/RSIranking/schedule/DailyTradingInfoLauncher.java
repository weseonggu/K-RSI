package com.service.RSIranking.schedule;

import com.service.RSIranking.config.krx_api.KrxApiProperties;
import com.service.RSIranking.util.DateUtil;
import com.service.RSIranking.util.IsClosedDay;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.configuration.JobRegistry;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.context.annotation.Configuration;

import java.text.SimpleDateFormat;
import java.util.Date;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class DailyTradingInfoLauncher {
    private final JobLauncher jobLauncher;
    private final JobRegistry jobRegistry;
    private final KrxApiProperties krxApiProperties;
    private final DateUtil dateUtil;
    private final IsClosedDay isClosedDay;
    private final AsyncJobLanucher asyncJobLanucher;

//    @Scheduled(cron = "45 * * * * *", zone = "Asia/Seoul")
    public void dailyTradingInfoSchedule() throws Exception{

        String yesterday = dateUtil.yesterday();
        if(dateUtil.isWeekend(yesterday)){
            log.info("주말 입니다. 일별 매매 정보 배치를 실행하지 않습니다.");
            return;
        }
        boolean isClosed = isClosedDay.isClosedDay(yesterday);
        if(!isClosed){
            log.info("종목 일별 매매 정보 업데이트 시작");
            dailyTradingInfoJobLauncher(yesterday);
        }else {
            log.info("휴장일 종목 일별 매매 정보 업데이트 없음");
        }
    }

    public void dailyTradingInfoJobLauncher(String yesterday) throws Exception{

        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd-hh-mm-ss");
        String date = dateFormat.format(new Date());

        JobParameters kospiJobParameters = new JobParametersBuilder()
                .addString("date", date)
                .addString("apiUrl", krxApiProperties.getKospiTradingInfoUrl())
                .addString("apiKey", krxApiProperties.getKey())
                .addString("mktNm", "KOSPI")
                .addString("yesterday", yesterday)
                .toJobParameters();

//        jobLauncher.run(jobRegistry.getJob("dailyTradingInformationUpdateJob"), kospiJobParameters);

        JobParameters kosdaqJobParameters = new JobParametersBuilder()
                .addString("date", date)
                .addString("apiUrl", krxApiProperties.getKosdaqTradingInfoUrl())
                .addString("apiKey", krxApiProperties.getKey())
                .addString("mktNm", "KOSDAQ")
                .addString("yesterday", yesterday)
                .toJobParameters();

//        jobLauncher.run(jobRegistry.getJob("dailyTradingInformationUpdateJob"), kosdaqJobParameters);
        asyncJobLanucher.runKospiTradingJob(kospiJobParameters);
        asyncJobLanucher.runKosdaqTradingJob(kosdaqJobParameters);
    }
}
