package com.service.RSIranking.dto;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.service.RSIranking.entity.EtfStockInfoEntity;
import com.service.RSIranking.entity.StockInfoEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Map;

/** ETF 일별 API의 종목코드와 종목명을 종목 마스터 형식으로 변환한다. */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonTypeName("ETF")
public class EtfSecuritiesStockDto implements Serializable, StockDto {
    private String isuCd;
    private String isuNm;
    private String mktNm;
    private String type;
    private Boolean isPublicStock;
    private boolean checked;

    public static EtfSecuritiesStockDto fromJson(Map<String, Object> stock, Boolean isPublicStock) {
        return EtfSecuritiesStockDto.builder()
                .isuCd(String.valueOf(stock.getOrDefault("ISU_CD", "N/A")))
                .isuNm(String.valueOf(stock.getOrDefault("ISU_NM", "N/A")))
                .mktNm("ETF")
                .type("ETF")
                .isPublicStock(isPublicStock)
                .build();
    }

    @Override
    public StockInfoEntity toEntity() {
        return StockInfoEntity.builder()
                .id(isuCd).isuNm(isuNm).mktNm(mktNm).isPublicStock(isPublicStock).build();
    }

    public EtfStockInfoEntity toEtfEntity() {
        return new EtfStockInfoEntity(this);
    }

    @Override
    public void updateChecked() {
        this.checked = true;
    }
}
