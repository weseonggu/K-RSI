package com.service.RSIranking.entity;

import com.service.RSIranking.dto.SecuritiesStockDto;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.*;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Builder
public class SecuritiesStockEntity {
    @Id
    @Column(name = "isu_cd", length = 100, nullable = false)
    private String id;  // 표준코드

    @Column(name = "isu_nm", length = 100, nullable = false)
    private String isuNm;  // 한글 종목명

    @Column(name = "mkt_nm", length = 20, nullable = false)
    private String mktNm;  // 코스피 코스닥

    @Column(name = "is_public_stock", nullable = false)
    private Boolean isPublicStock;

    //==============================================================================

    public void delistStock(){
        this.isPublicStock = false;
    }
    public void updateFromDto(SecuritiesStockDto dto){
        this.id = dto.getIsuCd();
        this.isuNm = dto.getIsuNm();
        this.mktNm = dto.getMktNm();
    }
    public SecuritiesStockEntity(SecuritiesStockDto dto) {
        this.id = dto.getIsuCd();
        this.isuNm = dto.getIsuNm();
        this.mktNm = dto.getMktNm();
        this.isPublicStock = true;
    }
}
