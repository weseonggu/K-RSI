package com.service.RSIranking.schedule;

import com.service.RSIranking.config.krx_api.KrxApiProperties;
import com.service.RSIranking.util.IsHoliday;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.configuration.JobRegistry;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Date;

@Configuration
@RequiredArgsConstructor
public class SecuritiesStockLauncher {


    private final JobLauncher jobLauncher;
    private final JobRegistry jobRegistry;
    private final KrxApiProperties krxApiProperties;
    private final IsHoliday isHoliday;

    @Scheduled(cron = "10 * * * * *", zone = "Asia/Seoul")
    public void kospiInfoUpdateJobLauncher() throws Exception{

        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd-hh-mm-ss");
        String date = dateFormat.format(new Date());

        // Todo 1차 어제 날짜가 주말인지 확인
        System.out.println("어제가 주말인가요? "+isHoliday.isWeekend());
        // Todo 2차 어제 날짜가 공휴일 인지 확인
        checkDate();

        String yesterday = yesterday();

        JobParameters jobParameters = new JobParametersBuilder()
                .addString("date", date)
                .addString("apiUrl", krxApiProperties.getKospiInfoUrl())
                .addString("apiKey", krxApiProperties.getKey())
                .addString("mktNm", "KOSPI")
                .addString("date", yesterday)
                .toJobParameters();

        jobLauncher.run(jobRegistry.getJob("stockUpdateJob"), jobParameters);
    }
    @Scheduled(cron = "10 * * * * *", zone = "Asia/Seoul")
    public void kosdaqInfoUpdateJobLauncher() throws Exception{

        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd-hh-mm-ss");
        String date = dateFormat.format(new Date());

        String yesterday = yesterday();

        JobParameters jobParameters = new JobParametersBuilder()
                .addString("date", date)
                .addString("apiUrl", krxApiProperties.getKosdaqInfoUrl())
                .addString("apiKey", krxApiProperties.getKey())
                .addString("mktNm", "KOSDAQ")
                .addString("date", yesterday)
                .toJobParameters();

        jobLauncher.run(jobRegistry.getJob("stockUpdateJob"), jobParameters);
    }

    /**
     * 어제 날짜 구하기
     * @return yyyyMMdd 날짜 문자열
     */
    private String yesterday(){
        return LocalDate.now().minusDays(1).format(DateTimeFormatter.ofPattern("yyyyMMdd"));
    }

    /**
     * 어제 날짜가 공휴일인지 확인
     * @return
     */
    private boolean checkDate(){
        // 날짜의 공휴일, 주말 여부 확인
        LocalDate yesterday = LocalDate.now().minusDays(1);
        int year = yesterday.getYear();
        int month = yesterday.getMonthValue();
        String data = isHoliday.getAnniversaryInfo(year, month);

        System.out.println(data);

        return true;
    }
}
