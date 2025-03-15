package com.service.RSIranking.dto;

import com.service.RSIranking.entity.SecuritiesStockEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Map;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KosdaqSecuritiesStockDto implements Serializable, StockDto {
    private String isuCd;
    private String isuNm;
    private String mktNm;
    private Boolean isPublicStock;
    private boolean checked = true;

    /**
     * JSON 데이터 SecuritiesStockDto로 변환
     * @param stock 종목 정보 데이터
     * @return SecuritiesStockDto 객체
     */
    public static KosdaqSecuritiesStockDto fromJson(Map<String, Object> stock, Boolean isPublicStock) {
        return KosdaqSecuritiesStockDto.builder()
                .isuCd((String) stock.getOrDefault("ISU_SRT_CD", "N/A"))
                .isuNm((String) stock.getOrDefault("ISU_NM", "N/A"))
                .mktNm((String) stock.getOrDefault("MKT_TP_NM", "N/A"))
                .isPublicStock(isPublicStock)
                .build();
    }

    /**
     * DTO를 엔티티로 변환
     * @return SecuritiesStockEntity
     */
    public SecuritiesStockEntity toEntity() {
        return SecuritiesStockEntity.builder()
                .id(this.isuCd)
                .isuNm(this.isuNm)
                .mktNm(this.mktNm)
                .isPublicStock(this.isPublicStock)
                .build();
    }

    /**
     * dto 확인 여부 엄데이트
     */
    public void updateChecked(){
        this.checked =true;
    }
}


