package com.service.RSIranking.dto;

import com.service.RSIranking.entity.SecuritiesStockEntity;

public interface StockDto {
    String getIsuCd();
    String getIsuNm();
    String getMktNm();
    void updateChecked();
    boolean isChecked();
    SecuritiesStockEntity toEntity();
}
