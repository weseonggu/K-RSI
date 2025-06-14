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
public class StockInfoLauncher {


    private final JobLauncher jobLauncher;
    private final JobRegistry jobRegistry;
    private final KrxApiProperties krxApiProperties;
    private final IsClosedDay isClosedDay;
    private final DateUtil dateUtil;
    private final AsyncJobLanucher asyncJobLanucher;

//    @Scheduled(cron = "5 * * * * *", zone = "Asia/Seoul")
    public void infoUpdateSchedule() throws Exception{

        String yesterday = dateUtil.yesterday();
        if(dateUtil.isWeekend(yesterday)){
            log.info("주말 입니다. 종목 업데이트 배치를 실행하지 않습니다.");
            return;
        }
        boolean isClosed = isClosedDay.isClosedDay(yesterday);
        if(!isClosed){
            log.info("종목 업데이트 시작");
            infoUpdateJobLauncher(yesterday);
        }else {
            log.info("휴장일 종목 업데이트 없음");
        }
    }


    public void infoUpdateJobLauncher(String yesterday) throws Exception{

        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd-hh-mm-ss");
        String date = dateFormat.format(new Date());

        JobParameters kospiJobParameters = new JobParametersBuilder()
                .addString("date", date)
                .addString("apiUrl", krxApiProperties.getKospiInfoUrl())
                .addString("apiKey", krxApiProperties.getKey())
                .addString("mktNm", "KOSPI")
                .addString("yesterday", yesterday)
                .toJobParameters();

//        jobLauncher.run(jobRegistry.getJob("stockUpdateJob"), kospiJobParameters);

        JobParameters kosdaqJobParameters = new JobParametersBuilder()
                .addString("date", date)
                .addString("apiUrl", krxApiProperties.getKosdaqInfoUrl())
                .addString("apiKey", krxApiProperties.getKey())
                .addString("mktNm", "KOSDAQ")
                .addString("yesterday", yesterday)
                .toJobParameters();

//        jobLauncher.run(jobRegistry.getJob("stockUpdateJob"), kosdaqJobParameters);

        asyncJobLanucher.runKospiInfoJob(kospiJobParameters);
        asyncJobLanucher.runKosdaqInfoJob(kosdaqJobParameters);

    }

}


