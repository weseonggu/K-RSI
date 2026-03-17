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
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * RSI 지표 계산 배치 스케줄러.
 *
 * <p>매일 정해진 시간에 RSI(Relative Strength Index) 지표 계산 배치 작업을
 * 실행합니다. 계산에 필요한 최근 14일간의 영업일 정보를 조회하여
 * 배치 작업에 전달합니다.</p>
 *
 * <p>실행 조건:</p>
 * <ul>
 *   <li>scheduler.rsiproducer.enabled=true 설정 시에만 활성화</li>
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
 * @see MarketDayForTheLast14Days
 * @see com.service.RSIranking.batch.rsi_calculation.RSICalculationBatch
 */
@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "scheduler.rsiproducer.enabled", havingValue = "true", matchIfMissing = false)
public class RSICalculationLauncher {
    
    private final KrxApiProperties krxApiProperties;
    private final DateUtil dateUtil;
    private final IsClosedDay isClosedDay;
    private final MarketDayForTheLast14Days marketDayForTheLast14Days;
    private final AsyncJobLauncher asyncJobLauncher;

    /**
     * RSI 지표 계산 스케줄 실행 메서드.
     *
     * <p>스케줄러에 의해 호출되어 어제 날짜 기준으로
     * RSI 지표 계산 배치를 실행합니다.</p>
     *
     * @throws Exception 배치 실행 중 오류 발생 시
     */
//    @Scheduled(cron = "50 * * * * *", zone = "Asia/Seoul")
    public void RSICalculationSchedule() throws Exception {
        String yesterday = dateUtil.yesterday();
        executeRSICalculation(yesterday);
    }

    /**
     * RSI 지표 계산을 실행합니다.
     *
     * <p>주말 및 휴장일 체크 후 RSI 계산에 필요한 최근 14일간의
     * 영업일 목록을 조회하여 배치 작업을 실행합니다.</p>
     *
     * @param date 계산 대상 날짜 (yyyyMMdd 형식)
     * @throws Exception 배치 실행 중 오류 발생 시
     */
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

    /**
     * RSI 지표 계산 배치 작업을 실행합니다.
     *
     * <p>KOSPI와 KOSDAQ 배치 작업을 비동기로 병렬 실행합니다.
     * 각 작업에는 다음 파라미터가 전달됩니다:</p>
     * <ul>
     *   <li>date: 실행 시간</li>
     *   <li>targetDate: RSI 계산 대상 날짜</li>
     *   <li>apiUrl: KRX API URL</li>
     *   <li>apiKey: KRX API 키</li>
     *   <li>mktNm: 시장 구분 (KOSPI/KOSDAQ)</li>
     *   <li>marketDayList: RSI 계산에 필요한 영업일 목록 (콤마 구분)</li>
     * </ul>
     *
     * @param targetDate RSI 계산 대상 날짜 (yyyyMMdd 형식)
     * @param marketDayList RSI 계산에 필요한 최근 14일간 영업일 목록
     * @throws Exception 배치 실행 중 오류 발생 시
     */
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

        // KOSDAQ Job 런처
        JobParameters kosdaqJobParameters = new JobParametersBuilder()
                .addString("date", jobExecutionTimestamp)
                .addString("targetDate", targetDate)
                .addString("apiUrl", krxApiProperties.getKosdaqInfoUrl())
                .addString("apiKey", krxApiProperties.getKey())
                .addString("mktNm", "KOSDAQ")
                .addString("marketDayList", marketDayListString)
                .toJobParameters();

        CompletableFuture<Void> kospiFuture = asyncJobLauncher.runKospiRSICalculationJob(kospiJobParameters);
        CompletableFuture<Void> kosdaqFuture = asyncJobLauncher.runKosdaqRSICalculationJob(kosdaqJobParameters);

        CompletableFuture.allOf(kospiFuture, kosdaqFuture)
                .thenRun(() -> log.info("모든 RSICalculation 배치 작업 완료!"))
                .exceptionally(ex -> {
                    log.error("RSICalculation 배치 작업 중 오류 발생: {}", ex.getMessage(), ex);
                    return null;
                })
                .get();

        log.info("KOSPI RSICalculation Job 완료: {}", kospiFuture.isCompletedExceptionally() ? "실패" : "성공");
        log.info("KOSDAQ RSICalculation Job 완료: {}", kosdaqFuture.isCompletedExceptionally() ? "실패" : "성공");
    }
}