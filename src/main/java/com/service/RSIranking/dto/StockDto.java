package com.service.RSIranking.dto;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.service.RSIranking.entity.StockInfoEntity;

/**
 * 종목 정보 DTO 인터페이스.
 *
 * <p>KOSPI/KOSDAQ 종목 정보 DTO의 공통 인터페이스입니다.
 * Jackson 다형성 지원을 위해 타입 정보를 포함합니다.</p>
 *
 * @author RSIranking Team
 * @version 1.0
 * @see KospiSecuritiesStockDto
 * @see KosdaqSecuritiesStockDto
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = KospiSecuritiesStockDto.class, name = "KOSPI"),
        @JsonSubTypes.Type(value = KosdaqSecuritiesStockDto.class, name = "KOSDAQ")
})
public interface StockDto {
    /** @return 종목 코드 */
    String getIsuCd();
    /** @return 종목명 */
    String getIsuNm();
    /** @return 시장 구분 */
    String getMktNm();
    /** 확인 상태를 true로 변경 */
    void updateChecked();
    /** @return 확인 여부 */
    boolean isChecked();
    /** @return 엔티티로 변환 */
    StockInfoEntity toEntity();
}
