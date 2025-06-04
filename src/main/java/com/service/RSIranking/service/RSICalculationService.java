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

    public void rsiCalculation(String isuCd, String targetDate, String marketDate){

        List<LocalDate> dates = new ArrayList<>();
        dates.add(LocalDate.parse(targetDate, formatter));

        List<LocalDate> marketDates = Arrays.stream(marketDate.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(dateStr -> LocalDate.parse(dateStr, formatter))
                .collect(Collectors.toList());

        dates.addAll(marketDates);

        List<DailyTradingInformation> tradingInfo = dailyTradingJDBCRepository.findByIsuCdAndDateIn(isuCd, dates);

        // todo 임시로 출력
        for (DailyTradingInformation data : tradingInfo){
            System.out.println("종목 코드: "+isuCd+" 상승,하락: "+data.getCmpprevddPrc()+" 비율: "+data.getFlucRt());
        }

        // todo tradingInfo의 사이즈에 따라 신규 기존을 구분하여 동작하도록
        if(tradingInfo.size() != 14){
            log.info("신규 종목 이므로 데이터가 더 필요합니다.");
            return;
        }

        boolean isNew = true;

        try {
            // 전날 평균 종가 상승/하락이 비어 있는지 확인 비어 있으면 신규 종목임
            isNew = findYesterdayAvgClosedInfo(marketDates.get(0), tradingInfo);
        }catch (NoSuchElementException e){
            return;
        }
        log.info("신규 종목인가요? "+ isNew);
        Double Ag = null;
        Double Al = null;
        Double RSI = null;
        if(isNew){
            // todo 일반적인 RSI계산
            Ag = simpleAGCalculation(tradingInfo);
            Al = simpleALCalculation(tradingInfo);
        }else {
            // todo 전일 평균 종가 상/하를 반영하기
            Ag = agCalculationWellesWilder(tradingInfo);
            Al = alCalculationWellesWilder(tradingInfo);
        }

        RSI = rsiCalculate(Ag, Al);

        if(Ag == null || Al == null || RSI == null) {
            return;
        }

        log.info("Ag: "+Ag + " Al: "+ Al + " RSI: " + RSI);
        updateTradingInfo(isuCd, targetDate, Ag, Al,RSI);

    }

//=========================================== 업데이트 ===================================
    @Transactional
    public void updateTradingInfo(String isuCD, String targetDate, Double Ag, Double Al, Double RSI){
        LocalDate date = LocalDate.parse(targetDate, formatter);
        DailyTradingInformation dailyInfo = securitiesStockRepository.findTradingInfoWithStock(isuCD, date)
                .orElseThrow(()-> new NoSuchElementException());
        log.info("종목: "+dailyInfo.getStock().getId());
        dailyInfo.updateRSIInfo(Ag,Al,RSI);
        dailyTradingInformationRepository.save(dailyInfo);
    }

// ==========================================================================================================================

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
            log.info("첫 번째 마켓 날짜에 해당하는 데이터: " + data.getTddClsprc());
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
        return avg == 0.0 ? null : avg;
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
        return avg == 0.0 ? null : avg;
    }

    /**
     * RSI 지표 계산
     * @param Ag Average Gain
     * @param Al Average Loss
     * @return RSI 지표 값
     */
    private Double rsiCalculate(Double Ag, Double Al){
        try {
            double rsiRaw = (Ag / (Ag + Al)) * 100;
            BigDecimal bd = new BigDecimal(rsiRaw).setScale(2, RoundingMode.HALF_UP);
            return bd.doubleValue();
        }catch (Exception e){
            return null;
        }
    }
}
