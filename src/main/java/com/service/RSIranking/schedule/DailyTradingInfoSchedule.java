package com.service.RSIranking.schedule;

import com.service.RSIranking.config.krx_api.KrxApiProperties;
import com.service.RSIranking.util.IsHoliday;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.configuration.JobRegistry;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.context.annotation.Configuration;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Date;

@Configuration
@RequiredArgsConstructor
public class DailyTradingInfoSchedule {
    private final JobLauncher jobLauncher;
    private final JobRegistry jobRegistry;
    private final KrxApiProperties krxApiProperties;
    private final IsHoliday isHoliday;

//    @Scheduled(cron = "40 * * * * *", zone = "Asia/Seoul")
    public void kospiDailyTradingInfoJobLauncher() throws Exception{

        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd-hh-mm-ss");
        String date = dateFormat.format(new Date());

        String yesterday = yesterday();

        JobParameters jobParameters = new JobParametersBuilder()
                .addString("date", date)
                .addString("apiUrl", krxApiProperties.getKospiTradingInfoUrl())
                .addString("apiKey", krxApiProperties.getKey())
                .addString("mktNm", "KOSPI")
                .addString("yesterday", yesterday)
                .toJobParameters();

        jobLauncher.run(jobRegistry.getJob("dailyTradingInformationUpdateJob"), jobParameters);
    }

//    @Scheduled(cron = "40 * * * * *", zone = "Asia/Seoul")
    public void kosdaqDailyTradingInfoJobLauncher() throws Exception{

        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd-hh-mm-ss");
        String date = dateFormat.format(new Date());

        String yesterday = yesterday();

        JobParameters jobParameters = new JobParametersBuilder()
                .addString("date", date)
                .addString("apiUrl", krxApiProperties.getKosdaqTradingInfoUrl())
                .addString("apiKey", krxApiProperties.getKey())
                .addString("mktNm", "KOSDAQ")
                .addString("yesterday", yesterday)
                .toJobParameters();

        jobLauncher.run(jobRegistry.getJob("dailyTradingInformationUpdateJob"), jobParameters);
    }
    // todo 중복 코드
    private String yesterday(){
        return LocalDate.now().minusDays(1).format(DateTimeFormatter.ofPattern("yyyyMMdd"));
    }
}
