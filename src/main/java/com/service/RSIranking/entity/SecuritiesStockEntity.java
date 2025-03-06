package com.service.RSIranking.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import lombok.*;

import java.util.Map;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Builder
public class SecuritiesStockEntity {
    @Id
    @Column(name = "isu_cd", length = 20, nullable = false)
    private String isuCd;  // 표준코드

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

}
