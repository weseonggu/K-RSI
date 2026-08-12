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
import org.springframework.scheduling.annotation.Schedules;
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
 * <p>기본값: 매주 화-토 08:05(1차) / 21:30(보정) (Asia/Seoul)</p>
 * <p>KRX는 전 거래일 데이터를 익일(토요일 포함) 아침 08:00 정각에 공개한다(운영 서버 프로브 실측).
 * "전일" 데이터를 수집하므로 요일 범위가 하루 밀린다 — 화요일 실행분이 월요일 데이터를,
 * 토요일 실행분이 금요일 데이터를 수집한다. MON-FRI를 쓰면 월요일 실행분은 yesterday=일요일이라
 * 스킵되고 금요일 데이터는 아무도 수집하지 않아 매주 하루씩 구멍이 생긴다.</p>
 * <p>21:30 실행분은 KRX 공개가 늦는 날을 위한 보정이다(같은 날짜 재수집, 멱등이라 중복 무해).</p>
 * <p>설정 변경: scheduler.master.cron(1차) / scheduler.master.retry-cron(보정) 프로퍼티</p>
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
     * <p>KRX 공개(08:00) 직후인 기본 08:05에 실행되고, 21:30에 같은 날짜를 한 번 더 보정 수집합니다.
     * 대상일(전일)이 주말 또는 휴장일인 경우 실행하지 않습니다.</p>
     *
     * @throws Exception 배치 실행 중 오류 발생 시
     */
    @Schedules({
            @Scheduled(cron = "${scheduler.master.cron:0 5 8 * * TUE-SAT}", zone = "Asia/Seoul"),
            @Scheduled(cron = "${scheduler.master.retry-cron:0 30 21 * * TUE-SAT}", zone = "Asia/Seoul")
    })
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
