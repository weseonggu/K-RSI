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
import java.util.concurrent.CompletableFuture;

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

        CompletableFuture<Void> kospiFuture = asyncJobLanucher.runKospiInfoJob(kospiJobParameters);
        Thread.sleep(200);
        CompletableFuture<Void> kosdaqFuture = asyncJobLanucher.runKosdaqInfoJob(kosdaqJobParameters);

        // 개별 완료 후 처리
        kospiFuture.whenComplete((result, ex) -> {
            if (ex != null) {
                log.info("KOSPI Stock Job 실패: " + ex.getMessage());
            } else {
                log.info("KOSPI Stock Job 완료");
            }
        });

        kosdaqFuture.whenComplete((result, ex) -> {
            if (ex != null) {
                log.info("KOSDAQ Stock Job 실패: " + ex.getMessage());
            } else {
                log.info("KOSDAQ Stock Job 완료");
            }
        });

        // 또는 두 작업 모두 완료된 후 실행
        CompletableFuture.allOf(kospiFuture, kosdaqFuture)
                .thenRun(() -> log.info("모든 Stock 배치 작업 완료!"));

    }

}


