package com.service.RSIranking.dto;

import com.service.RSIranking.entity.SecuritiesStockEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Map;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SecuritiesStockDto {
    private String isuCd;
    private String isuNm;
    private String mktNm;
    private Boolean isPublicStock;

    /**
     * JSON 데이터 SecuritiesStockDto로 변환
     * @param stock 종목 정보 데이터
     * @return SecuritiesStockDto 객체
     */
    public static SecuritiesStockDto fromJson(Map<String, Object> stock, Boolean isPublicStock) {
        return SecuritiesStockDto.builder()
                .isuCd((String) stock.getOrDefault("ISU_CD", "N/A"))
                .isuNm((String) stock.getOrDefault("ISU_NM", "N/A"))
                .mktNm((String) stock.getOrDefault("MKT_NM", "N/A"))
                .isPublicStock(isPublicStock)
                .build();
    }

    /**
     * DTO를 엔티티로 변환
     * @return SecuritiesStockEntity
     */
    public SecuritiesStockEntity toEntity() {
        return SecuritiesStockEntity.builder()
                .isuCd(this.isuCd)
                .isuNm(this.isuNm)
                .mktNm(this.mktNm)
                .isPublicStock(this.isPublicStock)
                .build();
    }
}


