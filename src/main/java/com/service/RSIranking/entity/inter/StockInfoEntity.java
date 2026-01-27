package com.service.RSIranking.entity.inter;

import com.service.RSIranking.dto.StockDto;

/**
 * 종목 정보 엔티티 인터페이스.
 *
 * <p>KOSPI/KOSDAQ 종목 정보 엔티티의 공통 인터페이스입니다.</p>
 *
 * @author RSIranking Team
 * @version 1.0
 */
public interface StockInfoEntity {

    /** @return 종목 코드 (표준코드) */
    String getId();
    /** @return 종목명 */
    String getIsuNm();
    /** @return 시장 구분 (KOSPI/KOSDAQ) */
    String getMktNm();
    /** @return 상장 여부 */
    Boolean getIsPublicStock();

    /** 상장폐지 처리 (isPublicStock = false) */
    void delistStock();
    /**
     * DTO 정보로 엔티티를 업데이트합니다.
     * @param dto 업데이트할 정보가 담긴 DTO
     */
    void updateFromDto(StockDto dto);
}
