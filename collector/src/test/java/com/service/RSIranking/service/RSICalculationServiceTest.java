package com.service.RSIranking.service;

import com.service.RSIranking.entity.KospiDailyTradingInformation;
import com.service.RSIranking.repository.jdbc.DailyTradingInformationJDBCRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link RSICalculationService} 순수 단위 테스트 (Mockito).
 *
 * <p>TDD 계획서: {@code _workflow/tdd/2026-07-05_rsi-trading-halt-volume-detection.md} 5.2절 T1~T16.</p>
 *
 * <p>모든 검증은 공개 API {@code rsiCalculation(...)}을 통해 수행하며, 신규 private 메서드
 * ({@code isVolumeZero}/{@code handleSuspendedDay} 등)를 직접 호출하지 않는다 (계획서 실행 지시).</p>
 *
 * <h2>테스트 전략</h2>
 * <ul>
 *   <li>{@code findByIsuCdAndDateIn}은 시나리오별 14건 리스트를 반환하도록 스텁한다.
 *       실제 리포지토리는 {@code ORDER BY date DESC}로 반환하므로, 픽스처도 날짜 내림차순
 *       (index0 = 대상일, index1 = 전일, ...)으로 구성한다.
 *       기존 Wilder 계산 메서드가 {@code datas.get(0)}/{@code datas.get(1)} 위치 인덱스에
 *       의존하기 때문에 이 정렬을 반드시 지켜야 한다.</li>
 *   <li>{@code updateRsi}는 성공 케이스에서 1을 반환하도록 스텁한다.
 *       ({@code updateTradingInfo}가 반환 0이면 {@link NoSuchElementException}을 던지므로.)</li>
 *   <li>{@link Strictness#LENIENT} 사용: Red(현재 구현) 단계와 Green(구현 후) 단계에서
 *       특정 스텁의 사용 여부가 달라져도 UnnecessaryStubbingException이 발생하지 않도록 한다.</li>
 * </ul>
 *
 * <h2>골든 데이터 오라클 (계획서 4.8절)</h2>
 * <p>T1과 T15의 RSI 골든값은 서비스 코드와 독립적으로 손계산 + 교차 계산기로 재검증했다.
 * 각 테스트 주석에 계산 전개를 남긴다.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RSICalculationServiceTest {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final LocalDate TARGET = LocalDate.of(2026, 6, 3);
    private static final LocalDate YESTERDAY = TARGET.minusDays(1);
    private static final String ISU_CD = "005930";
    private static final String MKT = "KOSPI";

    @Mock
    private DailyTradingInformationJDBCRepository repository;

    @InjectMocks
    private RSICalculationService service;

    @BeforeEach
    void setUp() {
        // 성공 UPDATE 기본 스텁 (updateTradingInfo 가 0 반환 시 예외를 던지므로).
        when(repository.updateRsi(anyString(), any(LocalDate.class), any(), any(), any(), anyString()))
                .thenReturn(1);
    }

    // ========================= 공용 픽스처 헬퍼 =========================

    private String targetDateStr() {
        return TARGET.format(FMT);
    }

    /** 전일(index0) 부터 과거 13일치 문자열. marketDates.get(0) == 전일. */
    private String marketDateStr() {
        return IntStream.rangeClosed(1, 13)
                .mapToObj(i -> TARGET.minusDays(i).format(FMT))
                .collect(Collectors.joining(","));
    }

    private KospiDailyTradingInformation.KospiDailyTradingInformationBuilder base(LocalDate date) {
        return KospiDailyTradingInformation.builder()
                .id(0L)
                .date(date)
                .tddClsprc(1000)
                .tddOpnprc(1000)
                .accTrdval(1_000_000L);
    }

    /** 정상 거래일: 거래량 > 0, 최소 한 필드가 0 이 아님(구 areAllFieldsZero 가 false 가 되도록). */
    private KospiDailyTradingInformation normalDay(LocalDate date, int cmpprev,
                                                   Double ag, Double al, Double rsi) {
        return base(date)
                .cmpprevddPrc(cmpprev)
                .flucRt(1.0)
                .tddHgprc(1100)
                .tddLwprc(900)
                .accTrdvol(1000L)
                .avgClosingGain(ag)
                .avgClosingLoss(al)
                .rsi(rsi)
                .build();
    }

    /** 거래정지일 (실제 KRX 패턴: 대비=0, 등락률=0, 고가=저가=0, 거래량=0). */
    private KospiDailyTradingInformation suspendedDay(LocalDate date,
                                                      Double ag, Double al, Double rsi) {
        return base(date)
                .cmpprevddPrc(0)
                .flucRt(0.0)
                .tddHgprc(0)
                .tddLwprc(0)
                .accTrdvol(0L)
                .avgClosingGain(ag)
                .avgClosingLoss(al)
                .rsi(rsi)
                .build();
    }

    /** index0(대상일)/index1(전일) 만 지정하고 나머지 12건은 정상 filler 로 채운 14건 리스트. */
    private List<KospiDailyTradingInformation> list14(KospiDailyTradingInformation index0,
                                                      KospiDailyTradingInformation index1) {
        List<KospiDailyTradingInformation> l = new ArrayList<>();
        l.add(index0);
        l.add(index1);
        for (int i = 2; i < 14; i++) {
            l.add(normalDay(TARGET.minusDays(i), 1, 1.0, 1.0, 50.0));
        }
        return l;
    }

    private void stubFind(List<KospiDailyTradingInformation> list) {
        when(repository.findByIsuCdAndDateIn(anyString(), anyList(), anyString())).thenReturn(list);
    }

    private void run() {
        service.rsiCalculation(ISU_CD, targetDateStr(), marketDateStr(), MKT);
    }

    // ================================ T1 ================================

    /**
     * T1 - 정상 케이스 회귀 (거래량 정상, 기존 종목).
     *
     * <p><b>골든 데이터 오라클 (계획서 4.8절 교차검증):</b>
     * 입력: 전일 Ag=2.0, Al=1.0, 대상일 대비(cmpprevddPrc)=+7.</p>
     * <pre>
     *   Ag_today = (2.0 * 13 + max(+7, 0)) / 14 = (26 + 7) / 14 = 33/14 = 2.357142857...
     *   Al_today = (1.0 * 13 + max(-7, 0)) / 14 = (13 + 0) / 14 = 13/14 = 0.928571428...
     *   RS       = Ag/Al = 33/13
     *   RSI      = Ag / (Ag + Al) * 100 = (33/14) / (46/14) * 100 = 33/46 * 100
     *            = 0.7173913043 * 100 = 71.73913043... -> HALF_UP(2) = 71.74
     * </pre>
     * 교차검증: 33/46 을 서비스와 무관하게 별도 계산 (부동소수 division: 0.717391304...),
     * 스프레드시트/전자계산기로 71.74 확인. 서비스 rsiCalculate 의 BigDecimal HALF_UP 도 71.74.
     */
    @Test
    @DisplayName("T1 정상 케이스 회귀 - Wilder 계산으로 Ag/Al/RSI 산출, updateRsi 1회")
    void t1_normalRegression() {
        KospiDailyTradingInformation target = normalDay(TARGET, 7, null, null, null);
        KospiDailyTradingInformation yesterday = normalDay(YESTERDAY, 3, 2.0, 1.0, 66.0);
        stubFind(list14(target, yesterday));

        run();

        ArgumentCaptor<Double> agCap = ArgumentCaptor.forClass(Double.class);
        ArgumentCaptor<Double> alCap = ArgumentCaptor.forClass(Double.class);
        ArgumentCaptor<Double> rsiCap = ArgumentCaptor.forClass(Double.class);
        verify(repository).updateRsi(eq(ISU_CD), eq(TARGET), agCap.capture(), alCap.capture(),
                rsiCap.capture(), eq(MKT));

        assertThat(agCap.getValue()).isCloseTo(33.0 / 14.0, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(alCap.getValue()).isCloseTo(13.0 / 14.0, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(rsiCap.getValue()).isEqualTo(71.74);
    }

    // ================================ T2 ================================

    /**
     * T2 - 정지 첫날: 대상일 accTrdvol=0, 전일은 정상(Ag/Al/RSI 보유).
     * 기대(신규 동작): 전일 Ag/Al/RSI 를 그대로 복사하여 updateRsi 1회.
     */
    @Test
    @DisplayName("T2 정지 첫날 - 전일 Ag/Al/RSI 그대로 복사")
    void t2_suspendedFirstDay_copiesYesterday() {
        KospiDailyTradingInformation target = suspendedDay(TARGET, null, null, null);
        KospiDailyTradingInformation yesterday = normalDay(YESTERDAY, 5, 2.5, 1.5, 62.5);
        stubFind(list14(target, yesterday));

        run();

        verify(repository).updateRsi(ISU_CD, TARGET, 2.5, 1.5, 62.5, MKT);
    }

    // ================================ T3 ================================

    /**
     * T3 - 연속 정지 2일차: 전일도 정지(이미 복사된 Ag/Al/RSI 보유).
     * 기대: 감쇠 없이 전일과 동일한 Ag/Al/RSI 를 다시 복사(평평히 유지).
     */
    @Test
    @DisplayName("T3 연속 정지 2일차 - 감쇠 없이 동일 Ag/Al/RSI 복사")
    void t3_suspendedSecondDay_flat() {
        KospiDailyTradingInformation target = suspendedDay(TARGET, null, null, null);
        // 전일도 정지이나, 전날 배치에서 복사되어 Ag/Al/RSI 를 이미 갖고 있는 상태.
        KospiDailyTradingInformation yesterday = suspendedDay(YESTERDAY, 4.0, 3.0, 57.14);
        stubFind(list14(target, yesterday));

        run();

        verify(repository).updateRsi(ISU_CD, TARGET, 4.0, 3.0, 57.14, MKT);
    }

    // ================================ T4 ================================

    /**
     * T4 - 재개 첫날: 대상일 accTrdvol>0, 전일(정지 마지막날) Ag/Al = 정지 진입 직전 값(유지됨).
     * 기대: 감쇠된 값이 아니라 유지된 X,Y 를 기준으로 Wilder 계산이 이어짐.
     * <pre>
     *   전일 Ag=3.0, Al=2.0, 대상일 대비=-14(손실)
     *   Ag_today = (3.0*13 + 0)/14 = 39/14 = 2.785714...
     *   Al_today = (2.0*13 + 14)/14 = 40/14 = 2.857142...
     * </pre>
     */
    @Test
    @DisplayName("T4 재개 첫날 - 유지된 Ag/Al 기준으로 Wilder 계산(감쇠 아님)")
    void t4_resumeDay_noDecay() {
        KospiDailyTradingInformation target = normalDay(TARGET, -14, null, null, null);
        KospiDailyTradingInformation yesterday = normalDay(YESTERDAY, 0, 3.0, 2.0, 60.0);
        stubFind(list14(target, yesterday));

        run();

        ArgumentCaptor<Double> agCap = ArgumentCaptor.forClass(Double.class);
        ArgumentCaptor<Double> alCap = ArgumentCaptor.forClass(Double.class);
        verify(repository).updateRsi(eq(ISU_CD), eq(TARGET), agCap.capture(), alCap.capture(),
                any(), eq(MKT));

        assertThat(agCap.getValue()).isCloseTo(39.0 / 14.0, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(alCap.getValue()).isCloseTo(40.0 / 14.0, org.assertj.core.data.Offset.offset(1e-9));
    }

    // ================================ T5 ================================

    /**
     * T5 - 장기 정지: 14일 전체 accTrdvol=0 (실제 KRX 패턴상 전 필드 0).
     * 기대: 전체 스킵, updateRsi 호출 없음.
     */
    @Test
    @DisplayName("T5 장기 정지(14일 전체 거래량 0) - 계산 스킵, updateRsi 미호출")
    void t5_longSuspension_skip() {
        List<KospiDailyTradingInformation> list = new ArrayList<>();
        for (int i = 0; i < 14; i++) {
            list.add(suspendedDay(TARGET.minusDays(i), null, null, null));
        }
        stubFind(list);

        run();

        verify(repository, never()).updateRsi(anyString(), any(), any(), any(), any(), anyString());
    }

    // ================================ T6 ================================

    /**
     * T6 - 정지일 + 전일 데이터 자체 없음 (방어적 상황).
     * 14건이지만 전일(marketDates.get(0)) 날짜 행이 리스트에 없음.
     * 기대: 예외 전파 없이 스킵, updateRsi 미호출.
     */
    @Test
    @DisplayName("T6 정지일 + 전일 데이터 없음 - 예외 없이 스킵")
    void t6_suspended_noYesterday() {
        KospiDailyTradingInformation target = suspendedDay(TARGET, null, null, null);
        // index1 의 날짜를 전일이 아닌 다른 날짜로 두어 전일 매칭 실패를 재현.
        List<KospiDailyTradingInformation> list = new ArrayList<>();
        list.add(target);
        for (int i = 2; i <= 14; i++) { // minusDays(2)..minusDays(14) → 전일(minusDays1) 부재
            list.add(normalDay(TARGET.minusDays(i), 1, 1.0, 1.0, 50.0));
        }
        stubFind(list);

        run(); // 예외 전파되지 않아야 함

        verify(repository, never()).updateRsi(anyString(), any(), any(), any(), any(), anyString());
    }

    // ================================ T7 ================================

    /**
     * T7 - 신규 종목(전일 Ag/Al/RSI 모두 null) + 대상일 정지.
     * [사용자 결정] 계산된 적 없는 값은 만들어내지 않는다 → 보류(updateRsi 미호출),
     * 대상일 Ag/Al/RSI 는 계속 null 로 남는 것이 허용된 동작.
     */
    @Test
    @DisplayName("T7 신규 종목 + 정지일 - 복사 보류, updateRsi 미호출")
    void t7_newStock_suspended_holds() {
        KospiDailyTradingInformation target = suspendedDay(TARGET, null, null, null);
        KospiDailyTradingInformation yesterday = normalDay(YESTERDAY, 1, null, null, null);
        stubFind(list14(target, yesterday));

        run();

        verify(repository, never()).updateRsi(anyString(), any(), any(), any(), any(), anyString());
    }

    // ================================ T8 ================================

    /**
     * T8 - accTrdvol == null 방어 (계획서 4.8절: Mockito 픽스처로만 재현 가능한 합성 케이스,
     * 실제 조회 경로 rs.getLong 은 null 을 반환하지 않음).
     * 기대: null 도 정지로 간주 → T2 와 동일하게 전일 Ag/Al/RSI 복사.
     */
    @Test
    @DisplayName("T8 accTrdvol=null 방어 - 정지로 간주하여 전일 값 복사")
    void t8_nullVolume_treatedAsSuspended() {
        KospiDailyTradingInformation target = base(TARGET)
                .cmpprevddPrc(0).flucRt(0.0).tddHgprc(0).tddLwprc(0)
                .accTrdvol(null) // 합성 케이스
                .avgClosingGain(null).avgClosingLoss(null).rsi(null)
                .build();
        KospiDailyTradingInformation yesterday = normalDay(YESTERDAY, 5, 2.5, 1.5, 62.5);
        stubFind(list14(target, yesterday));

        run();

        verify(repository).updateRsi(ISU_CD, TARGET, 2.5, 1.5, 62.5, MKT);
    }

    // ================================ T9 ================================

    /**
     * T9 - 조회 건수 14건 미만/초과 (기존 회귀). 여기서는 13건.
     * 기대: 계산 스킵, updateRsi 미호출.
     */
    @Test
    @DisplayName("T9 조회 건수 13건(<14) - 계산 스킵")
    void t9_insufficientRows_skip() {
        List<KospiDailyTradingInformation> list = new ArrayList<>();
        for (int i = 0; i < 13; i++) {
            list.add(normalDay(TARGET.minusDays(i), 1, 1.0, 1.0, 50.0));
        }
        stubFind(list);

        run();

        verify(repository, never()).updateRsi(anyString(), any(), any(), any(), any(), anyString());
    }

    // ================================ T10 ================================

    /**
     * T10 - 저유동성 종목의 하루 미체결(거래량 0)도 정지와 동일 취급 (의도된 동작).
     * 데이터 형태는 T2 와 동일: accTrdvol=0 이면 사유와 무관하게 전일 값 복사.
     */
    @Test
    @DisplayName("T10 미체결(거래량 0)도 정지와 동일 취급 - 전일 값 복사")
    void t10_zeroVolumeNoTrade_sameAsSuspended() {
        KospiDailyTradingInformation target = suspendedDay(TARGET, null, null, null);
        KospiDailyTradingInformation yesterday = normalDay(YESTERDAY, 9, 3.3, 1.1, 75.0);
        stubFind(list14(target, yesterday));

        run();

        verify(repository).updateRsi(ISU_CD, TARGET, 3.3, 1.1, 75.0, MKT);
    }

    // ================================ T11 ================================

    /**
     * T11 - 전일 데이터가 없는 일반(비정지) 케이스.
     * 기대: 기존과 동일하게 스킵(NoSuchElementException catch 경로), updateRsi 미호출.
     */
    @Test
    @DisplayName("T11 비정지 + 전일 데이터 없음 - 스킵")
    void t11_normal_noYesterday_skip() {
        KospiDailyTradingInformation target = normalDay(TARGET, 7, null, null, null);
        List<KospiDailyTradingInformation> list = new ArrayList<>();
        list.add(target);
        for (int i = 2; i <= 14; i++) { // 전일(minusDays1) 부재
            list.add(normalDay(TARGET.minusDays(i), 1, 1.0, 1.0, 50.0));
        }
        stubFind(list);

        run();

        verify(repository, never()).updateRsi(anyString(), any(), any(), any(), any(), anyString());
    }

    // ================================ T12 ================================

    /**
     * T12 - [리뷰어 필수 지적 1] 장기정지 종료 후 재개 첫날 극단값 재초기화 회귀 고정.
     * 과거 13일 accTrdvol=0(정지, Ag/Al/rsi=null 유지 - 장기정지라 복사 자체가 없었음),
     * 대상일 accTrdvol>0 · 대비=+500(재개 변동폭). 전일 Ag/Al=null → isNew=true(단순평균).
     * <pre>
     *   simpleAg = (+500 만 양수) / 14 = 500/14 = 35.714...
     *   simpleAl = 0/14 = 0
     *   RSI = Ag/(Ag+Al)*100 = 100.0  (극단값)
     * </pre>
     * 이 동작은 "수용된 기존 동작"(4.8절)으로 문서화하며 방어 로직을 추가하지 않는다.
     */
    @Test
    @DisplayName("T12 장기정지 종료 후 재개 - 극단값(RSI=100) 재초기화 회귀 고정")
    void t12_longSuspensionResume_extremeReinit() {
        KospiDailyTradingInformation target = normalDay(TARGET, 500, null, null, null);
        List<KospiDailyTradingInformation> list = new ArrayList<>();
        list.add(target);
        for (int i = 1; i < 14; i++) {
            list.add(suspendedDay(TARGET.minusDays(i), null, null, null)); // 정지, 미계산 상태
        }
        stubFind(list);

        run();

        ArgumentCaptor<Double> agCap = ArgumentCaptor.forClass(Double.class);
        ArgumentCaptor<Double> alCap = ArgumentCaptor.forClass(Double.class);
        ArgumentCaptor<Double> rsiCap = ArgumentCaptor.forClass(Double.class);
        verify(repository).updateRsi(eq(ISU_CD), eq(TARGET), agCap.capture(), alCap.capture(),
                rsiCap.capture(), eq(MKT));

        assertThat(agCap.getValue()).isCloseTo(500.0 / 14.0, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(alCap.getValue()).isEqualTo(0.0);
        assertThat(rsiCap.getValue()).isEqualTo(100.0);
    }

    // ================================ T13 ================================

    /**
     * T13 - [리뷰어 필수 지적 2] 복사 체인 경계: 윈도우에 정상 거래일 1건만 존재.
     * 대상일 포함 13건 accTrdvol=0, 가장 과거 1건만 accTrdvol>0.
     * 전일 Ag/Al/RSI = 복사된 값 존재.
     * 기대: isAllVolumeZero=false 이므로 정지일 처리 계속 → 전일 Ag/Al/RSI 복사, updateRsi 1회.
     * (T5 의 "윈도우 전체 0 → 스킵" 과 대비되는 경계.)
     */
    @Test
    @DisplayName("T13 복사 체인 경계 - 정상일 1건 존재 시 정지일 복사 계속")
    void t13_copyChainBoundary_continuesCopy() {
        KospiDailyTradingInformation target = suspendedDay(TARGET, null, null, null);
        KospiDailyTradingInformation yesterday = suspendedDay(YESTERDAY, 3.0, 2.0, 60.0); // 복사된 상태
        List<KospiDailyTradingInformation> list = new ArrayList<>();
        list.add(target);       // index0
        list.add(yesterday);    // index1
        for (int i = 2; i < 13; i++) {
            list.add(suspendedDay(TARGET.minusDays(i), null, null, null)); // 정지일
        }
        // index13(가장 과거): 유일한 정상 거래일
        list.add(normalDay(TARGET.minusDays(13), 5, 1.0, 1.0, 50.0));
        stubFind(list);

        run();

        verify(repository).updateRsi(ISU_CD, TARGET, 3.0, 2.0, 60.0, MKT);
    }

    // ================================ T14 ================================

    /**
     * T14 - [리뷰어 권장 2] 정지일 update 대상 행 없음 → 예외 전파 정책 검증.
     * 정지일 조건 충족하나 updateRsi 스텁이 0을 반환 → updateTradingInfo 가
     * NoSuchElementException 을 던지고, 이를 catch 하지 않고 rsiCalculation 밖으로 전파(4.7절).
     */
    @Test
    @DisplayName("T14 정지일 update 0건 - NoSuchElementException 전파")
    void t14_updateZeroRows_propagates() {
        KospiDailyTradingInformation target = suspendedDay(TARGET, null, null, null);
        KospiDailyTradingInformation yesterday = normalDay(YESTERDAY, 5, 2.0, 1.0, 50.0);
        stubFind(list14(target, yesterday));
        when(repository.updateRsi(anyString(), any(LocalDate.class), any(), any(), any(), anyString()))
                .thenReturn(0);

        assertThatThrownBy(this::run).isInstanceOf(NoSuchElementException.class);
    }

    // ================================ T15 ================================

    /**
     * T15 - 연속 정지 → 재개일 RSI 정확값 (감쇠 방지 정량 검증, 독립 교차검증 대상).
     *
     * <p><b>골든 데이터 오라클 (계획서 4.8절 교차검증):</b>
     * 전일(정지 마지막날) Ag=3.0, Al=2.0(정지 기간 내내 복사되어 유지된 값), 대상일 재개 대비=-14.</p>
     * <pre>
     *   Ag_today = (3.0*13 + max(-14,0))/14 = 39/14 = 2.7857142857...
     *   Al_today = (2.0*13 + max(14,0))/14  = (26+14)/14 = 40/14 = 2.8571428571...
     *   RSI = Ag/(Ag+Al)*100 = (39/14)/(79/14)*100 = 39/79*100
     *       = 0.4936708861*100 = 49.36708861... -> HALF_UP(2) = 49.37
     * </pre>
     * 교차검증: 39/79 를 서비스와 무관하게 별도 계산(전자계산기): 0.49367... → 49.37 확인.
     *
     * <p><b>감쇠 대비:</b> 만약 정지 기간 동안 Ag/Al 이 감쇠했다면(예: 3일 정지로 Ag 가
     * 3.0→(3*13/14)^3≈2.41 로 축소, Al 도 2.0→약1.61 로 축소) Ag'≈2.24, Al'≈2.49 →
     * RSI≈47.3 로 더 낮게(과매도 쪽으로) 왜곡되었을 것. 본 테스트는 감쇠 없이 49.37 이
     * 나옴을 고정하여 "감쇠 없이 이어짐"을 명시적으로 검증한다.</p>
     */
    @Test
    @DisplayName("T15 재개일 RSI 정확값(49.37) - 감쇠 없이 이어짐(독립 교차검증)")
    void t15_resumeDay_exactRsi_noDecay() {
        KospiDailyTradingInformation target = normalDay(TARGET, -14, null, null, null);
        KospiDailyTradingInformation yesterday = normalDay(YESTERDAY, 0, 3.0, 2.0, 60.0);
        stubFind(list14(target, yesterday));

        run();

        ArgumentCaptor<Double> agCap = ArgumentCaptor.forClass(Double.class);
        ArgumentCaptor<Double> alCap = ArgumentCaptor.forClass(Double.class);
        ArgumentCaptor<Double> rsiCap = ArgumentCaptor.forClass(Double.class);
        verify(repository).updateRsi(eq(ISU_CD), eq(TARGET), agCap.capture(), alCap.capture(),
                rsiCap.capture(), eq(MKT));

        assertThat(agCap.getValue()).isCloseTo(39.0 / 14.0, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(alCap.getValue()).isCloseTo(40.0 / 14.0, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(rsiCap.getValue()).isEqualTo(49.37);
    }

    // ================================ T16 ================================

    /**
     * T16 - [리뷰어 권장 3] cmpprevddPrc == null 방어 여부 문서화.
     *
     * <p>계획서 4.8절 결정: 정상 거래일의 {@code cmpprevddPrc}(Integer, nullable)에 대한
     * 방어 코드를 이번 스코프에서 추가하지 않는다(KRX 데이터는 정상 거래일에 항상 값이 채워진다는
     * 기존 가정 유지). 본 테스트는 <b>계약 문서화 테스트</b>로서, "정상 거래일 cmpprevddPrc 에
     * null 이 유입되면 언박싱 과정에서 NPE 로 조기 실패한다"는 현재/향후 동작을 고정한다.
     * 향후 실제 데이터에서 null 이 관측되어 이 가정이 깨지면 이 테스트가 재논의 신호가 된다.</p>
     */
    @Test
    @DisplayName("T16 정상 거래일 cmpprevddPrc=null - NPE 로 조기 실패(계약 문서화)")
    void t16_nullCmpprevddPrc_throwsNpe() {
        KospiDailyTradingInformation target = base(TARGET)
                .cmpprevddPrc(null) // 언박싱 NPE 유발
                .flucRt(1.0).tddHgprc(1100).tddLwprc(900).accTrdvol(1000L)
                .avgClosingGain(2.0).avgClosingLoss(1.0).rsi(50.0)
                .build();
        KospiDailyTradingInformation yesterday = normalDay(YESTERDAY, 3, 2.0, 1.0, 66.0);
        stubFind(list14(target, yesterday));

        assertThatThrownBy(this::run).isInstanceOf(NullPointerException.class);
    }
}
