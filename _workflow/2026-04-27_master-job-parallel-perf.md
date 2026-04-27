# 마스터 Job 병렬처리 성능 개선

> 작성일: 2026-04-27
> 브랜치: `fix/master-job-parallel-perf`
> 원 계획서: `.plan/master-job-parallel-issue-analysis.md`

## 작업 목적

마스터 잡(`masterPipelineJob`) 도입 후 KOSPI/KOSDAQ 병렬 처리가 사실상 직렬화되어 약 2배 느려진 문제를 해결한다.
주된 원인은 (1) 마스터 Step Tx가 자식 Job 종료까지 meta DB 커넥션을 30초 이상 점유, (2) HikariCP 풀 부족, (3) JobRepository SERIALIZABLE 락 경합의 조합.

본 작업에서는 원 계획서의 **Phase 1 (성능 핵심) + Phase 2 (측정/진단) + Phase 3 (구조 안전화)** 를 묶어 진행한다.
Phase 4(자식 Step Tx 정리), Phase 5(검증), Phase 6(실 데이터 러너)는 별도 후속 작업으로 분리.

## 변경 대상 파일

### Phase 1 — 성능 핵심
- `src/main/java/com/service/RSIranking/batch/master_job/MasterJobBatchConfig.java`
  - 마스터 Step의 PlatformTransactionManager를 `metaTransactionManager` → `ResourcelessTransactionManager`로 교체
- `src/main/java/com/service/RSIranking/config/DB/MetaDBConfig.java` & `JPADataDBConfig.java`
  - HikariCP 풀 사이즈 명시 (코드에서 직접 설정 — yml 바인딩 없이도 동작 보장)
- `src/main/resources/application-dev.yml` (운영/테스트 통합 동일 적용)
  - HikariCP 풀 설정값 yml 노출 (옵션, 향후 튜닝 위해)

### Phase 2 — 측정/진단
- `src/main/java/com/service/RSIranking/schedule/AsyncJobLauncher.java`
  - 각 `runKospi*Job` / `runKosdaq*Job` 진입·종료에 스레드 이름 + nanoTime 로깅
- `src/main/java/com/service/RSIranking/batch/measurement/JobExecutionTimeListener.java`
- `src/main/java/com/service/RSIranking/batch/measurement/StepExecutionTimeListener.java`
- `src/main/java/com/service/RSIranking/batch/master_job/listener/MasterJobExecutionListener.java`
  - 인스턴스 필드 `startTime` 제거 → `JobExecution.getStartTime()` / `getEndTime()` 사용 (race 제거)

### Phase 3 — 구조 안전화
- `src/main/java/com/service/RSIranking/batch/master_job/step/StockUpdateJobStepTasklet.java`
- `src/main/java/com/service/RSIranking/batch/master_job/step/TradingInfoJobStepTasklet.java`
- `src/main/java/com/service/RSIranking/batch/master_job/step/RSICalculationJobStepTasklet.java`
  - `@StepScope` 적용 (인스턴스 필드 race 차단)
  - `JobParameters` 추출을 `@Value("#{jobParameters['...']}")` SpEL로 이동
  - `StepExecutionListener` 구현 분리 가능성 검토 (현재는 단순 로그만 있어 유지)
- `src/main/java/com/service/RSIranking/schedule/AsyncJobLauncher.java`
  - 자식 `JobExecution.getStatus()` 검사 → `BatchStatus.COMPLETED`가 아니면 `failedFuture(...)` 반환

## 구현 단계

1. **Phase 2-1, 2-2**: 측정 인프라(스레드 로그 + Listener race 제거) 먼저 적용 → 효과 측정 가능하게
2. **Phase 1-1**: 마스터 Step Tx 매니저 → `ResourcelessTransactionManager`
3. **Phase 1-2**: HikariCP 풀 사이즈 명시 (코드 + yml)
4. **Phase 3-3**: 자식 Job 실패 처리 정확도 향상 (`JobExecution.getStatus()` 검사)
5. **Phase 3-1, 3-2**: 마스터 Tasklet `@StepScope` + SpEL JobParameters 주입
6. 빌드: `./gradlew build -x test`
7. 테스트 작성 + 통과: `./gradlew test`

## 테스트 시나리오

기존 `MasterJobExecutionTest`는 그대로 통과해야 한다 (regression).

추가로:
- **AsyncJobLauncherTest** (단위): JobLauncher가 FAILED인 JobExecution을 반환하면 `failedFuture`를 돌려주는지 검증
- **MasterJobParallelExecutionTest** (통합): 마스터 Job 실행 시 KOSPI/KOSDAQ가 다른 스레드에서 실행되며 시간 구간이 겹치는지 검증 (Phase 1-1 + Async 동작 회귀 테스트)

## 주의 사항

- `ResourcelessTransactionManager` 적용 시 마스터 Step의 chunk write가 meta DB에 가지 않음 — 마스터 Tasklet은 DB I/O 없는 dispatcher이므로 문제 없음. 단, JobRepository의 step_execution 갱신은 별도 Tx로 처리되므로 영향 없음.
- HikariCP 풀 설정은 `DataSourceBuilder` 빌드 후 캐스팅해서 직접 set (yml 바인딩이 prefix 매칭 의존이라 누락 위험 회피).
- `@StepScope`는 Tasklet에 SpEL 주입을 활성화하기 위해 필요하며, 동시에 인스턴스 필드 race도 자동 차단.
