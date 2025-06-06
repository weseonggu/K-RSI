package com.service.RSIranking.service;

import com.service.RSIranking.entity.DailyTradingInformation;
import com.service.RSIranking.repository.jdbc.DailyTradingInformationJDBCRepository;
import com.service.RSIranking.repository.jpa.DailyTradingInformationRepository;
import com.service.RSIranking.repository.jpa.SecuritiesStockRepository;
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

@Service
@RequiredArgsConstructor
@Slf4j
public class RSICalculationService {

    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd");
    private final DailyTradingInformationJDBCRepository dailyTradingJDBCRepository;
    private final SecuritiesStockRepository securitiesStockRepository;
    private final DailyTradingInformationRepository dailyTradingInformationRepository;

    /**
     * RSI 계산 도메인 로직
     * @param isuCd 종목 코드
     * @param targetDate 업데이트 날짜
     * @param marketDate 과거 14일 장날 문자열
     */
    public void rsiCalculation(String isuCd, String targetDate, String marketDate){

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

        // 종목 코드와 14일 날짜를 사용해서 매매 정보 조회 JDBC사용
        List<DailyTradingInformation> tradingInfo = dailyTradingJDBCRepository.findByIsuCdAndDateIn(isuCd, dates);

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
            updateTradingInfo(isuCd, targetDate, Ag, Al,RSI);

        }catch (RuntimeException e){
            throw e;
        }

    }

//=========================================== RSI 업데이트 =========================================================
    @Transactional
    public void updateTradingInfo(String isuCD, String targetDate, Double Ag, Double Al, Double RSI) throws  RuntimeException{
        LocalDate date = LocalDate.parse(targetDate, formatter);
        DailyTradingInformation dailyInfo = securitiesStockRepository.findTradingInfoWithStock(isuCD, date)
                .orElseThrow(()-> new NoSuchElementException());
        log.info("종목: "+dailyInfo.getStock().getId());
        dailyInfo.updateRSIInfo(Ag,Al,RSI);
        dailyTradingInformationRepository.save(dailyInfo);
    }

// ===============================================신규 종목인지 아닌지 파악하는 메소드===============================================

    /**
     * 신규 종목인지 아닌지 파악하는 메소드
     * @param yesterday 업데이트 하고자하는 날짜의 전날짜
     * @param tradingInfo 데이터
     * @return T/F
     * @throws NoSuchElementException
     */
    private boolean findYesterdayAvgClosedInfo(LocalDate yesterday, List<DailyTradingInformation> tradingInfo) throws NoSuchElementException{
        Optional<DailyTradingInformation> targetData = tradingInfo.stream()
                .filter(info -> info.getDate().equals(yesterday))
                .findFirst();

        if (targetData.isPresent()) {
            DailyTradingInformation data = targetData.get();
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
    private boolean areAllFieldsZero(List<DailyTradingInformation> tradingInfoList) {
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
    private Double simpleAGCalculation(List<DailyTradingInformation> datas){
        Double avg = 0.0;
        Double sum = 0.0;
        for(DailyTradingInformation data : datas){
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
    private Double agCalculationWellesWilder(List<DailyTradingInformation> datas){
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
    private Double simpleALCalculation(List<DailyTradingInformation> datas){
        Double avg = 0.0;
        Double sum = 0.0;
        for(DailyTradingInformation data : datas){
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
    private Double alCalculationWellesWilder(List<DailyTradingInformation> datas){
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
