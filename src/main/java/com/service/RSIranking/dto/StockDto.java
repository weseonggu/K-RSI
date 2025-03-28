package com.service.RSIranking.dto;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.service.RSIranking.entity.SecuritiesStockEntity;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = KospiSecuritiesStockDto.class, name = "KOSPI"),
        @JsonSubTypes.Type(value = KosdaqSecuritiesStockDto.class, name = "KOSDAQ")
})
public interface StockDto {
    String getIsuCd();
    String getIsuNm();
    String getMktNm();
    void updateChecked();
    boolean isChecked();
    SecuritiesStockEntity toEntity();
}
