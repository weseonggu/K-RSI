# 실 데이터 100일치 RSI 수집 러너 (Phase 6)

> 작성일: 2026-04-27
> 브랜치: `feat/live-100day-rsi-runner`
> 원 계획서: `.plan/master-job-parallel-issue-analysis.md` Phase 6

## 작업 목적

Phase 1~3 수정의 효과를 **실 인프라**(localhost:3308 data DB / 3307 meta DB / 6380 Redis)에서 종단 검증하기 위한 재실행 가능한 러너 구축.

기존 `RealDataCollectionTest`는 Testcontainers 일회용이라 매 실행마다 컨테이너를 새로 띄우고 TRUNCATE한다. 신규 러너는 실 dev 인프라에 붙어 누적/갱신만 수행하고, 실패 후 재실행이 자연스럽다.

## 핵심 제약

- **수집 순서**: 과거 → 최근 단방향 (RSI lookback 14일 보장)
- **실 인프라 사용**: `application-dev.yml` 그대로. `AbstractIntegrationTest` 상속 금지 (Testcontainers 회피).
- **TRUNCATE 금지**: upsert 전제로 누적/갱신만.
- **CI 우발 실행 방지**: `@EnabledIfSystemProperty(named="rsi.live.run", matches="true")` 가드.
- **재실행 가능성(resume)**: 같은 명령 재실행 시 미수집 일자만 진행.

## 변경 대상 파일

### 신규
- `src/test/java/com/service/RSIranking/integration/batch/support/LiveJobInvoker.java`
  - `RealDataCollectionTest`의 launch* 메서드를 헬퍼로 추출. 두 테스트가 공유.
- `src/test/java/com/service/RSIranking/integration/batch/live/Live100DayRsiCollectionRunner.java`
  - 본 작업의 메인. preflight → stockInfo → tradingInfo → RSI → verify 순으로 5개 @Order 메서드.

### 수정 (선택적 — 중복 코드 제거)
- `src/test/java/com/service/RSIranking/integration/batch/RealDataCollectionTest.java`
  - launch* 메서드를 LiveJobInvoker로 위임.

## 구현 단계

1. `LiveJobInvoker` 신설 (Spring Component 아닌 일반 헬퍼 클래스, 의존성은 생성자 주입).
2. `Live100DayRsiCollectionRunner` 신설.
   - 클래스 가드: `@EnabledIfSystemProperty(named="rsi.live.run", matches="true")`
   - `@SpringBootTest @ActiveProfiles("dev") @TestInstance(PER_CLASS) @TestMethodOrder(OrderAnnotation.class)`
   - `Order(1) preflight_checkInfra()`: data/meta DB 연결, Redis PING, 현재 종목/매매 상태 로깅 + resume 기준점 파악
   - `Order(2) collect_stockInfo_pastToRecent()`: 영업일 114개, 과거 → 최근. throttle.
   - `Order(3) collect_tradingInfo_pastToRecent()`: 동일.
   - `Order(4) calculate_rsi_pastToRecent()`: index 14 ~ 113 (= 최근 100일).
   - `Order(5) verify_finalState()`: 종목/매매 임계, RSI 메시지 수, 마지막 영업일 데이터 존재.
3. `RealDataCollectionTest` 리팩터링 (launch* → invoker 위임)
4. 빌드 확인: `./gradlew build -x test`
5. 컴파일만 검증 (러너는 가드 때문에 일반 test에서 비활성).

## 주요 설계 결정

- **`LiveJobInvoker`를 Spring Bean으로 만들지 않음**: 테스트 한정 헬퍼이므로 단순 POJO. 테스트가 자기 의존성을 주입한 후 invoker를 생성한다.
- **resume의 기준**: data DB의 `kospi_daily_trading_information.date` DISTINCT 집합. 이미 그 일자의 trading 행이 있으면 SKIP. 가장 안정적인 데이터(매매 정보 = 일자별 한 번만 수집되는 fact)로 판단.
- **RSI 일자 범위**: 영업일 114개를 만들고 `index 14 ~ 113` (총 100개)에 RSI 계산 호출. 앞 14개는 lookback 용도로만 수집 (수집은 되지만 RSI 계산은 하지 않음).
- **휴장일 필터**: `generate114MarketDays`에서 주말은 이미 제외했고, KRX 휴장일은 일자별 수집 시 KRX API가 빈 응답을 주므로 자연 처리. `IsClosedDay`로 사전 필터하면 추가 API 호출이 비싸므로 생략.
- **throttle**: `rsi.live.intervalMs` 시스템 프로퍼티 (기본 200ms). 일자 사이 sleep.
- **실패 정책**: 부분 실패 허용. 한 일자 실패해도 즉시 중단하지 않고 결과 누적 후 다음으로. 마지막에 실패 일자 목록 출력.

## 테스트 시나리오

이 러너는 그 자체가 시나리오. 단위 테스트 없음.

검증 방법:
- 컴파일 통과 (`./gradlew compileTestJava`).
- 가드 비활성화 시 일반 `./gradlew test`에서 SKIP.
- 명시적 실행: `./gradlew test --tests Live100DayRsiCollectionRunner -Drsi.live.run=true` (사용자 직접 실행).

## 주의 사항

- 이 러너는 dev DB에 직접 쓰기 때문에 **CI에서 자동 실행되면 안 된다**. 가드가 핵심.
- KRX API key는 `application-dev.yml`의 것을 그대로 사용.
- 100일치 ≈ 114일 수집 + 100일 RSI 계산 → API 호출 약 (114 × 4) + (100 × 2) ≈ 약 660회. throttle 200ms 기준 ≈ 132초의 sleep + 실 호출 시간. 총 수십 분 예상.
