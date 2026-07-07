package com.service.RSIranking.schedule;

import com.service.RSIranking.util.DateUtil;
import com.service.RSIranking.util.IsClosedDay;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * 마스터 파이프라인 Job 스케줄러.
 *
 * <p>매일 정해진 시간에 마스터 파이프라인 Job을 실행합니다.
 * 종목 정보 수집 -> 매매 정보 수집 -> RSI 계산을 순차적으로 실행합니다.</p>
 *
 * <h2>실행 조건</h2>
 * <ul>
 *   <li>scheduler.master.enabled=true 설정 시에만 활성화</li>
 *   <li>주말이 아닌 경우에만 실행</li>
 *   <li>휴장일이 아닌 경우에만 실행</li>
 * </ul>
 *
 * <h2>스케줄 설정</h2>
 * <p>기본값: 매주 월-금 18:30 (Asia/Seoul)</p>
 * <p>설정 변경: scheduler.master.cron 프로퍼티로 변경 가능</p>
 *
 * @author RSIranking Team
 * @version 1.0
 * @see com.service.RSIranking.batch.master_job.MasterJobBatchConfig
 */
@Service
@Slf4j
@ConditionalOnProperty(name = "scheduler.master.enabled", havingValue = "true", matchIfMissing = false)
public class MasterJobLauncher {

    private final JobLauncher jobLauncher;
    private final Job masterPipelineJob;
    private final DateUtil dateUtil;
    private final IsClosedDay isClosedDay;

    public MasterJobLauncher(
            JobLauncher jobLauncher,
            @Qualifier("masterPipelineJob") Job masterPipelineJob,
            DateUtil dateUtil,
            IsClosedDay isClosedDay) {
        this.jobLauncher = jobLauncher;
        this.masterPipelineJob = masterPipelineJob;
        this.dateUtil = dateUtil;
        this.isClosedDay = isClosedDay;
    }

    /**
     * 마스터 파이프라인 스케줄 실행 메서드.
     *
     * <p>장 마감 후(기본: 18:30) 실행됩니다.
     * 주말 또는 휴장일인 경우 실행하지 않습니다.</p>
     *
     * @throws Exception 배치 실행 중 오류 발생 시
     */
    @Scheduled(cron = "${scheduler.master.cron:0 30 18 * * MON-FRI}", zone = "Asia/Seoul")
    public void masterPipelineSchedule() throws Exception {
        String yesterday = dateUtil.yesterday();

        if (dateUtil.isWeekend(yesterday)) {
            log.info("주말입니다. 마스터 파이프라인을 실행하지 않습니다.");
            return;
        }

        try {
            if (isClosedDay.isClosedDay(yesterday)) {
                log.info("휴장일입니다. 마스터 파이프라인을 실행하지 않습니다.");
                return;
            }
        } catch (Exception e) {
            log.error("휴장일 확인 실패로 오늘의 마스터 파이프라인 실행을 중단합니다. 수동 재실행이 필요합니다. 대상일: {}", yesterday, e);
            return;
        }

        log.info("마스터 파이프라인 스케줄 실행 - 대상일: {}", yesterday);
        executeMasterPipeline(yesterday);
    }

    /**
     * 마스터 파이프라인 Job을 실행합니다.
     *
     * <p>외부에서 특정 날짜로 수동 실행할 때 사용합니다.
     * 테스트에서도 이 메서드를 직접 호출할 수 있습니다.</p>
     *
     * @param targetDate 처리 대상 날짜 (yyyyMMdd 형식)
     * @throws Exception Job 실행 중 오류 발생 시
     */
    public void executeMasterPipeline(String targetDate) throws Exception {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss");
        String executionDate = dateFormat.format(new Date());

        JobParameters jobParameters = new JobParametersBuilder()
                .addString("executionDate", executionDate)
                .addString("yesterday", targetDate)
                .toJobParameters();

        log.info("마스터 파이프라인 Job 시작 - 실행시간: {}, 대상일: {}", executionDate, targetDate);
        jobLauncher.run(masterPipelineJob, jobParameters);
    }
}
