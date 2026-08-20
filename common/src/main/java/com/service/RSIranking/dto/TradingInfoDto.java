package com.service.RSIranking.dto;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * 일별 매매 정보 DTO 인터페이스.
 *
 * <p>KOSPI/KOSDAQ 일별 매매 정보 DTO의 공통 인터페이스입니다.</p>
 *
 * @author RSIranking Team
 * @version 1.0
 * @see KospiTradingInfoDto
 * @see KosdaqTradingInfoDto
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = KospiTradingInfoDto.class, name = "KOSPI"),
        @JsonSubTypes.Type(value = KosdaqTradingInfoDto.class, name = "KOSDAQ"),
        @JsonSubTypes.Type(value = EtfTradingInfoDto.class, name = "ETF")
})
public interface TradingInfoDto {
     /** @return 기준일자 (yyyyMMdd) */
     String getBasDd();
     /** @return 종목 코드 */
     String getIsuCd();
     /** @return 종목명 */
     String getIsuNm();
     /** @return 시장 구분 */
     String getMktNm();
     /** @return 종가 */
     String getTddClsprc();
     /** @return 대비 (전일 대비 가격 변동) */
     String getCmpprevddPrc();
     /** @return 등락률 */
     String getFlucRt();
     /** @return 시가 */
     String getTddOpnprc();
     /** @return 고가 */
     String getTddHgprc();
     /** @return 저가 */
     String getTddLwprc();
     /** @return 거래량 */
     String getAccTrdvol();
     /** @return 거래대금 */
     String getAccTrdval();
     /** @return 타입 (KOSPI/KOSDAQ) */
     String getType();
}
