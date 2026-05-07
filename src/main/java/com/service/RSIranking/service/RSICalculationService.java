package com.service.RSIranking.service;

import com.service.RSIranking.entity.KospiDailyTradingInformation;
import com.service.RSIranking.repository.jdbc.DailyTradingInformationJDBCRepository;
import com.service.RSIranking.repository.jpa.DailyTradingInformationRepository;
import com.service.RSIranking.repository.jpa.KospiStockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final KospiStockRepository kospiStockRepository;
    private final DailyTradingInformationRepository dailyTradingInformationRepository;

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

        // tradingInfo의 사이즈가 14이면 메세지 처리 안함 신규 종목이여서
        if(tradingInfo.size() != 14){
            log.info("신규 종목 이므로 데이터가 더 필요합니다.");
            return;
        }
        if(areAllFieldsZero(tradingInfo)){
            log.info("거래 정지 종목 입니다.");
            return;
        }

        boolean isNew = true;

        try {
            // 전날 평균 종가 상승/하락이 비어 있는지 확인 비어 있으면 신규 종목임
            isNew = findYesterdayAvgClosedInfo(marketDates.get(0), tradingInfo);
        }catch (NoSuchElementException e){
            return;
        }

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

        log.info("Ag: "+Ag + " Al: "+ Al + " RSI: " + RSI);
        try {
            updateTradingInfo(isuCd, targetDate, Ag, Al, RSI, mktNm);

        }catch (RuntimeException e){
            throw e;
        }

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
     * @param isuCD      종목 코드
     * @param targetDate 업데이트 대상 날짜 (yyyyMMdd 형식)
     * @param Ag         평균 상승폭 (Average Gain)
     * @param Al         평균 하락폭 (Average Loss)
     * @param RSI        계산된 RSI 값
     * @param mktNm      시장 구분 ("KOSPI" / "KOSDAQ")
     * @throws RuntimeException 업데이트 실패 시 발생
     * @throws NoSuchElementException 해당 종목/날짜의 거래 정보가 없을 경우 발생
     */
    @Transactional
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
     * 신규 종목인지 아닌지 파악하는 메소드
     * @param yesterday 업데이트 하고자하는 날짜의 전날짜
     * @param tradingInfo 데이터
     * @return T/F
     * @throws NoSuchElementException
     */
    private boolean findYesterdayAvgClosedInfo(LocalDate yesterday, List<KospiDailyTradingInformation> tradingInfo) throws NoSuchElementException{
        Optional<KospiDailyTradingInformation> targetData = tradingInfo.stream()
                .filter(info -> info.getDate().equals(yesterday))
                .findFirst();

        if (targetData.isPresent()) {
            KospiDailyTradingInformation data = targetData.get();
            if(data.getAvgClosingGain() == null || data.getAvgClosingLoss() == null){
                return true;
            }else {
                return false;
            }
        } else {
            log.info("해당 날짜의 데이터가 없습니다.");
            throw new NoSuchElementException("해당 날짜의 데이터가 없습니다.");
        }
    }
    //=================================== 거래 정지 확인=============================================
    /**
     * 거래 정지 종목 여부를 확인합니다.
     *
     * <p>모든 거래 정보의 대비, 등락률, 고가, 저가가 0인 경우 거래 정지 종목으로 판단합니다.</p>
     *
     * @param tradingInfoList 거래 정보 목록
     * @return 거래 정지 종목이면 {@code true}, 아니면 {@code false}
     */
    private boolean areAllFieldsZero(List<KospiDailyTradingInformation> tradingInfoList) {
        return tradingInfoList.stream().allMatch(info ->
                info.getCmpprevddPrc() == 0 &&
                        info.getFlucRt() == 0 &&
                        info.getTddHgprc() == 0 &&
                        info.getTddLwprc() == 0
        );
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
