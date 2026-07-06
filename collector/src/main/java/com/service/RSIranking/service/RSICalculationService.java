package com.service.RSIranking.service;

import com.service.RSIranking.entity.KospiDailyTradingInformation;
import com.service.RSIranking.repository.jdbc.DailyTradingInformationJDBCRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * RSI(Relative Strength Index, 상대강도지수) 계산 서비스.
 *
 * <p>RSI는 주식의 과매수/과매도 상태를 판단하는 기술적 지표로,
 * 0~100 사이의 값을 가지며 일반적으로 70 이상은 과매수, 30 이하는 과매도로 해석됩니다.</p>
 *
 * <h2>RSI 계산 공식</h2>
 * <pre>
 * RSI = 100 - (100 / (1 + RS))
 * RS = Average Gain / Average Loss
 * </pre>
 *
 * <h2>지원하는 계산 방식</h2>
 * <ul>
 *   <li><b>단순 평균 방식</b> - 신규 종목에 적용, 14일간의 단순 평균</li>
 *   <li><b>Welles Wilder 방식</b> - 기존 종목에 적용, 지수이동평균(EMA) 기반</li>
 * </ul>
 *
 * @author RSIranking Team
 * @version 1.0
 * @see <a href="https://www.investopedia.com/terms/r/rsi.asp">RSI 설명</a>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RSICalculationService {

    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd");
    private final DailyTradingInformationJDBCRepository dailyTradingJDBCRepository;

    /**
     * RSI 계산 도메인 로직
     * @param isuCd 종목 코드
     * @param targetDate 업데이트 날짜
     * @param marketDate 과거 14일 장날 문자열
     * @param mktNm 시장 구분 ("KOSPI" / "KOSDAQ") - 분리된 테이블 라우팅에 사용
     */
    public void rsiCalculation(String isuCd, String targetDate, String marketDate, String mktNm){

        List<LocalDate> dates = new ArrayList<>();
        // 업데이트할 장 날짜 문자열 타입 변환
        dates.add(LocalDate.parse(targetDate, formatter));
        // 과거 장 날짜 문자열 타입 변환
        List<LocalDate> marketDates = Arrays.stream(marketDate.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(dateStr -> LocalDate.parse(dateStr, formatter))
                .collect(Collectors.toList());
        // 14일 날짜 합치기
        dates.addAll(marketDates);

        // 종목 코드와 14일 날짜를 사용해서 매매 정보 조회 JDBC사용 (시장별 분리 테이블)
        List<KospiDailyTradingInformation> tradingInfo = dailyTradingJDBCRepository.findByIsuCdAndDateIn(isuCd, dates, mktNm);

        // 조회 결과가 14건이 아니면 계산 불가 - 원인별로 구분해서 남긴다
        if(tradingInfo.size() < 14){
            log.info("RSI 계산 스킵(데이터 부족 - 신규 상장 또는 수집 누락) - 시장: {}, 종목: {}, 대상일: {}, 조회 건수: {}/14",
                    mktNm, isuCd, targetDate, tradingInfo.size());
            return;
        }
        if(tradingInfo.size() > 14){
            // 유니크 제약(isu_cd, date)이 없거나 깨진 상태에서 배치가 재실행되면 발생한다
            log.warn("RSI 계산 스킵(중복 데이터 감지) - 시장: {}, 종목: {}, 대상일: {}, 조회 건수: {}/14 - DB 중복 정리가 필요합니다",
                    mktNm, isuCd, targetDate, tradingInfo.size());
            return;
        }
        // 14일 윈도우 전체가 거래량 0(장기 거래정지)이면 계산/복사 모두 중단하고 스킵한다.
        if(isAllVolumeZero(tradingInfo)){
            log.info("RSI 계산 스킵(장기 거래정지 - 14일 전체 거래량 0) - 시장: {}, 종목: {}, 대상일: {}", mktNm, isuCd, targetDate);
            return;
        }

        // 대상일 행을 위치 인덱스가 아니라 날짜 매칭으로 조회한다 (ORDER BY date DESC 정렬에 우연히 의존하지 않도록).
        LocalDate targetLocalDate = dates.get(0);
        Optional<KospiDailyTradingInformation> targetInfoOpt = tradingInfo.stream()
                .filter(info -> info.getDate().equals(targetLocalDate))
                .findFirst();
        if(targetInfoOpt.isEmpty()){
            log.warn("RSI 계산 스킵(대상일 데이터 없음) - 시장: {}, 종목: {}, 대상일: {}", mktNm, isuCd, targetDate);
            return;
        }

        // 대상일 거래량이 0(거래정지/미체결)이면 전일 Ag/Al/RSI 를 그대로 복사한다.
        if(isVolumeZero(targetInfoOpt.get())){
            handleSuspendedDay(isuCd, targetDate, marketDates.get(0), tradingInfo, mktNm);
            return;
        }

        boolean isNew;

        KospiDailyTradingInformation yesterdayInfo;
        try {
            // 전일 엔티티를 조회 (없으면 NoSuchElementException)
            yesterdayInfo = findYesterdayTradingInfo(marketDates.get(0), tradingInfo);
        }catch (NoSuchElementException e){
            log.warn("RSI 계산 스킵(전일 데이터 없음) - 시장: {}, 종목: {}, 대상일: {}, 전일: {}",
                    mktNm, isuCd, targetDate, marketDates.get(0));
            return;
        }

        // 전날 평균 종가 상승/하락이 비어 있으면 신규 종목으로 판단한다.
        isNew = yesterdayInfo.getAvgClosingGain() == null || yesterdayInfo.getAvgClosingLoss() == null;

        // RSI를 계산하기 위해서 평균 종가 상승/하락폭 계산 후 RSI 계산
        // todo 고도화 필요 변수가 많음
        Double Ag = null;
        Double Al = null;
        Double RSI = null;
        if(isNew){
            // 일반적인 RSI계산 신규 종목 일때 실행되는 곳
            log.info("일반적인 RSI계산 신규 종목");
            Ag = simpleAGCalculation(tradingInfo);
            Al = simpleALCalculation(tradingInfo);
        }else {
            // 기존 RSI 계산이 필요한 경우 전일 평균 종가 상/하를 반영하여 계산
            log.info("기존 RSI 계산이 필요한 경우 전일 평균 종가 상/하를 반영");
            Ag = agCalculationWellesWilder(tradingInfo);
            Al = alCalculationWellesWilder(tradingInfo);
        }

        RSI = rsiCalculate(Ag, Al);

        if(Ag == null || Al == null || RSI == null) {
            return;
        }

        log.info("RSI 계산 완료 - 시장: {}, 종목: {}, 대상일: {}, Ag: {}, Al: {}, RSI: {}", mktNm, isuCd, targetDate, Ag, Al, RSI);
        updateTradingInfo(isuCd, targetDate, Ag, Al, RSI, mktNm);
    }

//=========================================== RSI 업데이트 =========================================================
    /**
     * 계산된 RSI 정보를 데이터베이스에 업데이트합니다.
     *
     * <p>{@code mktNm}에 따라 {@code kospi_daily_trading_information} 또는
     * {@code kosdaq_daily_trading_information} 테이블에 직접 UPDATE를 수행합니다.
     * 분리 전 단일 {@code daily_trading_information} 테이블을 사용하던 JPA 경로는
     * 분리 이후 데이터를 찾지 못하므로 JDBC update로 대체했습니다.</p>
     *
     * <p>단일 UPDATE 문이므로 별도 트랜잭션 경계가 필요 없다.
     * (기존의 @Transactional은 자기 호출이라 프록시를 타지 않아 적용되지 않았음)</p>
     *
     * @param isuCD      종목 코드
     * @param targetDate 업데이트 대상 날짜 (yyyyMMdd 형식)
     * @param Ag         평균 상승폭 (Average Gain)
     * @param Al         평균 하락폭 (Average Loss)
     * @param RSI        계산된 RSI 값
     * @param mktNm      시장 구분 ("KOSPI" / "KOSDAQ")
     * @throws RuntimeException 업데이트 실패 시 발생
     * @throws NoSuchElementException 해당 종목/날짜의 거래 정보가 없을 경우 발생
     */
    public void updateTradingInfo(String isuCD, String targetDate, Double Ag, Double Al, Double RSI, String mktNm) throws  RuntimeException{
        LocalDate date = LocalDate.parse(targetDate, formatter);
        int updated = dailyTradingJDBCRepository.updateRsi(isuCD, date, Ag, Al, RSI, mktNm);
        if (updated == 0) {
            throw new NoSuchElementException(
                    "RSI 업데이트 대상 행이 없습니다. mktNm=" + mktNm + ", isuCd=" + isuCD + ", date=" + date);
        }
    }

// ===============================================신규 종목인지 아닌지 파악하는 메소드===============================================

    /**
     * 전일 매매 정보 엔티티를 조회합니다.
     *
     * <p>전일 Ag/Al 이 둘 다 존재하면 기존 종목, 하나라도 null 이면 신규 종목으로 판단하는 데
     * 사용됩니다. 정지일 처리 시에는 전일 Ag/Al/RSI 값을 그대로 복사하기 위해 엔티티 자체를 반환합니다.</p>
     *
     * @param yesterday 업데이트 하고자하는 날짜의 전날짜
     * @param tradingInfo 데이터
     * @return 전일 매매 정보 엔티티
     * @throws NoSuchElementException 전일 날짜의 데이터가 없을 경우
     */
    private KospiDailyTradingInformation findYesterdayTradingInfo(LocalDate yesterday, List<KospiDailyTradingInformation> tradingInfo) throws NoSuchElementException{
        return tradingInfo.stream()
                .filter(info -> info.getDate().equals(yesterday))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("해당 날짜의 데이터가 없습니다."));
    }

    //=================================== 거래 정지 확인=============================================
    /**
     * 단일 매매 정보의 거래량이 0인지(거래정지/미체결) 확인합니다.
     *
     * <p>{@code accTrdvol == null} 도 거래량 0과 동일하게 정지로 간주합니다(방어적 처리).</p>
     *
     * @param info 거래 정보
     * @return 거래량이 0이거나 null 이면 {@code true}
     */
    private boolean isVolumeZero(KospiDailyTradingInformation info) {
        Long vol = info.getAccTrdvol();
        return vol == null || vol == 0L;
    }

    /**
     * 14일 윈도우 전체의 거래량이 0인지(장기 거래정지) 확인합니다.
     *
     * @param tradingInfoList 거래 정보 목록
     * @return 전체가 거래량 0이면 {@code true}
     */
    private boolean isAllVolumeZero(List<KospiDailyTradingInformation> tradingInfoList) {
        return tradingInfoList.stream().allMatch(this::isVolumeZero);
    }

    /**
     * 거래정지일 처리: 전일 Ag/Al/RSI 를 그대로 복사합니다.
     *
     * <p>정지일은 변동폭이 0이므로 Wilder 평균이 변하지 않아야 하며, 증권사/거래소 차트처럼
     * 정지 기간 중 RSI 가 정지 진입 직전 값으로 평평하게 유지되도록 전일 값을 그대로 복사합니다.
     * 전일 Ag/Al 이 하나라도 null 이면(신규 상장 직후 정지 등 복사할 이력 없음) 계산된 적 없는
     * 값을 만들어내지 않기 위해 보류(스킵)합니다. {@code updateTradingInfo} 호출 결과 예외는
     * catch 하지 않고 그대로 전파합니다(4.7절, 기존 정상 흐름과 동일한 정책).</p>
     *
     * @param isuCd       종목 코드
     * @param targetDate  대상 날짜 (yyyyMMdd)
     * @param yesterday   전일 날짜
     * @param tradingInfo 14일 매매 정보 목록
     * @param mktNm       시장 구분
     */
    private void handleSuspendedDay(String isuCd, String targetDate, LocalDate yesterday,
                                    List<KospiDailyTradingInformation> tradingInfo, String mktNm) {
        KospiDailyTradingInformation yesterdayInfo;
        try {
            yesterdayInfo = findYesterdayTradingInfo(yesterday, tradingInfo);
        } catch (NoSuchElementException e) {
            log.warn("RSI 계산 스킵(거래정지일, 전일 데이터 없음) - 시장: {}, 종목: {}, 대상일: {}, 전일: {}",
                    mktNm, isuCd, targetDate, yesterday);
            return;
        }

        Double prevAg = yesterdayInfo.getAvgClosingGain();
        Double prevAl = yesterdayInfo.getAvgClosingLoss();
        Double prevRsi = yesterdayInfo.getRsi();

        if (prevAg == null || prevAl == null) {
            // 복사할 이력이 없으므로 보류 - 이 행의 Ag/Al/RSI 는 계속 NULL 로 남는다.
            log.info("RSI 계산 보류(거래정지일, 전일 Ag/Al/RSI 없음) - 시장: {}, 종목: {}, 대상일: {}", mktNm, isuCd, targetDate);
            return;
        }

        log.info("거래정지일 - 전일 Ag/Al/RSI 복사 - 시장: {}, 종목: {}, 대상일: {}, Ag: {}, Al: {}, RSI: {}",
                mktNm, isuCd, targetDate, prevAg, prevAl, prevRsi);
        updateTradingInfo(isuCd, targetDate, prevAg, prevAl, prevRsi, mktNm);
    }
//======================================================RSI 계산===============================================================
    /**
     * Average Gain 계산
     * @param datas 데이터
     * @return Average Gain
     */
    private Double simpleAGCalculation(List<KospiDailyTradingInformation> datas){
        Double avg = 0.0;
        Double sum = 0.0;
        for(KospiDailyTradingInformation data : datas){
            if(data.getCmpprevddPrc()>=0){
                sum += data.getCmpprevddPrc();
            }
        }
        avg = sum/14;
        return avg;
    }
    /**
     * Welles Wilder Average Gain 계산
     * @param datas 데이터
     * @return Welles Wilder Average Gain
     */
    private Double agCalculationWellesWilder(List<KospiDailyTradingInformation> datas){
        Double avg = 0.0;
        Double cmpprevddPrc = datas.get(0).getCmpprevddPrc() > 0 ? datas.get(0).getCmpprevddPrc() : 0.0;
        Double sum = (datas.get(1).getAvgClosingGain()*13) + cmpprevddPrc;
        avg = sum/14;
        return avg;
    }

    /**
     * Average Loss 계산
     * @param datas 데이터
     * @return Average Loss
     */
    private Double simpleALCalculation(List<KospiDailyTradingInformation> datas){
        Double avg = 0.0;
        Double sum = 0.0;
        for(KospiDailyTradingInformation data : datas){
            if(data.getCmpprevddPrc()<=0){
                sum += data.getCmpprevddPrc()*-1;
            }
        }
        avg = sum/14;
        return avg;
    }
    /**
     * Welles Wilder Average Loss 계산
     * @param datas 데이터
     * @return Average Loss
     */
    private Double alCalculationWellesWilder(List<KospiDailyTradingInformation> datas){
        Double avg = 0.0;
        Double cmpprevddPrc = datas.get(0).getCmpprevddPrc() < 0 ? datas.get(0).getCmpprevddPrc()*-1 : 0.0;
        Double sum = (datas.get(1).getAvgClosingLoss()*13) + cmpprevddPrc;
        avg = sum/14;
        return avg;
    }

    /**
     * RSI 지표 계산
     * @param Ag Average Gain
     * @param Al Average Loss
     * @return RSI 지표 값
     */
    private Double rsiCalculate(Double Ag, Double Al) {
        try {
            if (Ag == null || Al == null) {
                return null; // 입력값 중 하나라도 null이면 계산 불가
            }

            double denominator = Ag + Al;

            if (denominator == 0.0) {
                return 100.0; // 보통 RSI 계산 시 분모가 0이면 RSI는 100 또는 0으로 정의
            }

            double rsiRaw = (Ag / denominator) * 100;
            BigDecimal bd = new BigDecimal(rsiRaw).setScale(2, RoundingMode.HALF_UP);
            return bd.doubleValue();

        } catch (Exception e) {
            return null; // 그 외 예외는 null 반환
        }
    }
}
