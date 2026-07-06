package com.service.RSIranking.entity.inter;

import java.time.LocalDate;

/**
 * 일별 매매 정보 엔티티 인터페이스.
 *
 * <p>KOSPI/KOSDAQ 일별 매매 정보 엔티티의 공통 인터페이스입니다.</p>
 *
 * @author RSIranking Team
 * @version 1.0
 */
public interface DailyTradingInformation {

    /** @return 거래일자 */
    LocalDate getDate();

    /** @return 종가 */
    Integer getTddClsprc();

    /** @return 대비 (전일 대비 가격 변동) */
    Integer getCmpprevddPrc();

    /** @return 등락률 */
    Double getFlucRt();

    /** @return 시가 */
    Integer getTddOpnprc();

    /** @return 고가 */
    Integer getTddHgprc();

    /** @return 저가 */
    Integer getTddLwprc();

    /** @return 거래량 */
    Long getAccTrdvol();

    /** @return 거래대금 */
    Long getAccTrdval();

    /** @return RSI 지표 값 */
    Double getRsi();

    /** @return 평균 상승폭 (Average Gain) */
    Double getAvgClosingGain();

    /** @return 평균 하락폭 (Average Loss) */
    Double getAvgClosingLoss();

    /**
     * RSI 관련 정보를 업데이트합니다.
     *
     * @param ag  평균 상승폭
     * @param al  평균 하락폭
     * @param rsi RSI 지표 값
     */
    void updateRSIInfo(Double ag, Double al, Double rsi);

}
