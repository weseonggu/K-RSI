package com.service.RSIranking.dto;

import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Map;

/**
 * KOSDAQ 일별 매매 정보 DTO.
 *
 * <p>KRX API 응답을 파싱하여 KOSDAQ 일별 매매 정보를 담는 DTO입니다.</p>
 *
 * @author RSIranking Team
 * @version 1.0
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonTypeName("KOSDAQ")
public class KosdaqTradingInfoDto implements Serializable, TradingInfoDto{
    private String basDd;		// 기준일자
    private String isuCd;		//종목코드
    private String isuNm;		//종목명
    private String mktNm;	    //시장구분
    private String tddClsprc;	//종가
    private String cmpprevddPrc;//대비
    private String flucRt;		 //등락률
    private String tddOpnprc;	 //시가
    private String tddHgprc;	 //고가
    private String tddLwprc;	 //저가
    private String accTrdvol;	 //거래량
    private String accTrdval;	 //거래대금
    private String type;

    /**
     * JSON 데이터 KosdaqTradingInfoDto로 변환
     * @param stock 일별 매매 정보 데이터
     * @return KosdaqTradingInfoDto 객체
     */
    public static KosdaqTradingInfoDto fromJson(Map<String, Object> stock) {
        return KosdaqTradingInfoDto.builder()
                .basDd((String) stock.getOrDefault("BAS_DD", "N/A"))
                .isuCd((String) stock.getOrDefault("ISU_CD", "N/A"))
                .isuNm((String) stock.getOrDefault("ISU_NM", "N/A"))
                .mktNm((String) stock.getOrDefault("MKT_NM", "KOSDAQ"))
                .tddClsprc((String) stock.getOrDefault("TDD_CLSPRC", "N/A"))
                .cmpprevddPrc((String) stock.getOrDefault("CMPPREVDD_PRC", "N/A"))
                .flucRt((String) stock.getOrDefault("FLUC_RT", "N/A"))
                .tddOpnprc((String) stock.getOrDefault("TDD_OPNPRC", "N/A"))
                .tddHgprc((String) stock.getOrDefault("TDD_HGPRC", "N/A"))
                .tddLwprc((String) stock.getOrDefault("TDD_LWPRC", "N/A"))
                .accTrdvol((String) stock.getOrDefault("ACC_TRDVOL", "N/A"))
                .accTrdval((String) stock.getOrDefault("ACC_TRDVAL", "N/A"))
                .type((String) stock.getOrDefault("MKT_NM", "KOSDAQ"))
                .build();
    }
}

