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
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Configuration
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "scheduler.dailytranding.enabled", havingValue = "true", matchIfMissing = false)
public class DailyTradingInfoLauncher {
    private final JobLauncher jobLauncher;
    private final JobRegistry jobRegistry;
    private final KrxApiProperties krxApiProperties;
    private final DateUtil dateUtil;
    private final IsClosedDay isClosedDay;
    private final AsyncJobLanucher asyncJobLanucher;

//    @Scheduled(cron = "30 * * * * *", zone = "Asia/Seoul")
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
                .addString("uuid", UUID.randomUUID().toString())
                .addString("date", date)
                .addString("apiUrl", krxApiProperties.getKospiTradingInfoUrl())
                .addString("apiKey", krxApiProperties.getKey())
                .addString("mktNm", "KOSPI")
                .addString("yesterday", yesterday)
                .toJobParameters();

//        jobLauncher.run(jobRegistry.getJob("dailyTradingInformationUpdateJob"), kospiJobParameters);

        JobParameters kosdaqJobParameters = new JobParametersBuilder()
                .addString("uuid", UUID.randomUUID().toString())
                .addString("date", date)
                .addString("apiUrl", krxApiProperties.getKosdaqTradingInfoUrl())
                .addString("apiKey", krxApiProperties.getKey())
                .addString("mktNm", "KOSDAQ")
                .addString("yesterday", yesterday)
                .toJobParameters();

//        jobLauncher.run(jobRegistry.getJob("dailyTradingInformationUpdateJob"), kosdaqJobParameters);
        CompletableFuture<Void> kospiFuture = asyncJobLanucher.runKospiTradingJob(kospiJobParameters);
        Thread.sleep(200);
        CompletableFuture<Void> kosdaqFuture = asyncJobLanucher.runKosdaqTradingJob(kosdaqJobParameters);

        // 모든 작업 완료를 기다림 (blocking)
        CompletableFuture.allOf(kospiFuture, kosdaqFuture).get();

        // 개별 완료 후 처리
        kospiFuture.whenComplete((result, ex) -> {
            if (ex != null) {
                log.info("KOSPI Tranding Job 실패: " + ex.getMessage());
            } else {
                log.info("KOSPI Tranding Job 완료");
            }
        });

        kosdaqFuture.whenComplete((result, ex) -> {
            if (ex != null) {
                log.info("KOSDAQ Tranding Job 실패: " + ex.getMessage());
            } else {
                log.info("KOSDAQ Tranding Job 완료");
            }
        });

        // 또는 두 작업 모두 완료된 후 실행
        CompletableFuture.allOf(kospiFuture, kosdaqFuture)
                .thenRun(() -> log.info("모든 Tranding 배치 작업 완료!"));
    }
}
