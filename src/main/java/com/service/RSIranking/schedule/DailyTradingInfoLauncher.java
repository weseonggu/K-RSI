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

/**
 * 일별 매매 정보 업데이트 배치 스케줄러.
 *
 * <p>매일 정해진 시간에 KRX API로부터 일별 매매 정보를 가져와
 * 데이터베이스에 업데이트하는 배치 작업을 실행합니다.</p>
 *
 * <p>실행 조건:</p>
 * <ul>
 *   <li>scheduler.dailytranding.enabled=true 설정 시에만 활성화</li>
 *   <li>주말이 아닌 경우에만 실행</li>
 *   <li>휴장일이 아닌 경우에만 실행</li>
 * </ul>
 *
 * <p>KOSPI와 KOSDAQ 배치 작업을 비동기로 병렬 실행하여
 * 전체 처리 시간을 단축합니다.</p>
 *
 * @author RSIranking Team
 * @version 1.0
 * @see AsyncJobLauncher
 * @see com.service.RSIranking.batch.daily_trading_info.DailyTradingInformationUpdateBatch
 */
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
    private final AsyncJobLauncher asyncJobLauncher;

    /**
     * 일별 매매 정보 업데이트 스케줄 실행 메서드.
     *
     * <p>스케줄러에 의해 호출되어 어제 날짜 기준으로
     * 일별 매매 정보 업데이트 배치를 실행합니다.</p>
     *
     * <p>주말 또는 휴장일인 경우 배치를 실행하지 않습니다.</p>
     *
     * @throws Exception 배치 실행 중 오류 발생 시
     */
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

    /**
     * 일별 매매 정보 업데이트 배치 작업을 실행합니다.
     *
     * <p>KOSPI와 KOSDAQ 배치 작업을 비동기로 병렬 실행합니다.
     * 각 작업에는 다음 파라미터가 전달됩니다:</p>
     * <ul>
     *   <li>uuid: 작업 고유 식별자</li>
     *   <li>date: 실행 시간</li>
     *   <li>apiUrl: KRX API URL</li>
     *   <li>apiKey: KRX API 키</li>
     *   <li>mktNm: 시장 구분 (KOSPI/KOSDAQ)</li>
     *   <li>yesterday: 조회 대상 날짜</li>
     * </ul>
     *
     * @param yesterday 조회 대상 날짜 (yyyyMMdd 형식)
     * @throws Exception 배치 실행 중 오류 발생 시
     */
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

        JobParameters kosdaqJobParameters = new JobParametersBuilder()
                .addString("uuid", UUID.randomUUID().toString())
                .addString("date", date)
                .addString("apiUrl", krxApiProperties.getKosdaqTradingInfoUrl())
                .addString("apiKey", krxApiProperties.getKey())
                .addString("mktNm", "KOSDAQ")
                .addString("yesterday", yesterday)
                .toJobParameters();

        CompletableFuture<Void> kospiFuture = asyncJobLauncher.runKospiTradingJob(kospiJobParameters);
        CompletableFuture<Void> kosdaqFuture = asyncJobLauncher.runKosdaqTradingJob(kosdaqJobParameters);

        CompletableFuture.allOf(kospiFuture, kosdaqFuture)
                .thenRun(() -> log.info("모든 Trading 배치 작업 완료!"))
                .exceptionally(ex -> {
                    log.error("Trading 배치 작업 중 오류 발생: {}", ex.getMessage(), ex);
                    return null;
                })
                .get();

        log.info("KOSPI Trading Job 완료: {}", kospiFuture.isCompletedExceptionally() ? "실패" : "성공");
        log.info("KOSDAQ Trading Job 완료: {}", kosdaqFuture.isCompletedExceptionally() ? "실패" : "성공");
    }
}
