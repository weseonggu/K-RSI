package com.service.RSIranking.entity.inter;

import com.service.RSIranking.dto.StockDto;
import com.service.RSIranking.entity.DailyTradingInformation;

import java.util.List;

public interface StockInfoEntity {

    String getId();                     // 표준코드
    String getIsuNm();                  // 종목명
    String getMktNm();                  // 시장명
    Boolean getIsPublicStock();         // 상장여부
//    List<DailyTradingInformation> getTradingInfo();  // 거래 정보 리스트

    void delistStock();                // 상장 폐지 처리
    void updateFromDto(StockDto dto);  // DTO 기반 정보 업데이트
}
