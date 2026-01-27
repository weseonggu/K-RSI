package com.service.RSIranking.entity;

import com.service.RSIranking.dto.StockDto;
import com.service.RSIranking.entity.inter.StockInfoEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * KOSDAQ 종목 정보 엔티티.
 *
 * <p>KOSDAQ 시장 종목의 기본 정보를 저장하는 JPA 엔티티입니다.
 * 일별 매매 정보와 OneToMany 관계를 가집니다.</p>
 *
 * @author RSIranking Team
 * @version 1.0
 */
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Builder
@Table(
        name = "kosdaq_stock_info"
)
public class KosdaqStockInfoEntity implements StockInfoEntity {
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

    @OneToMany(mappedBy = "stock", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<KosdaqDailyTradingInformation> tradingInfo;

    //==============================================================================

    public void delistStock(){
        this.isPublicStock = false;
    }
    public void updateFromDto(StockDto dto){
        if (!this.isuNm.equals(dto.getIsuNm())) {
            this.isuNm = dto.getIsuNm();
        }
        if (!this.mktNm.equals(dto.getMktNm())) {
            this.mktNm = dto.getMktNm();
        }
    }
    public KosdaqStockInfoEntity(StockDto dto) {
        this.id = dto.getIsuCd();
        this.isuNm = dto.getIsuNm();
        this.mktNm = dto.getMktNm();
        this.isPublicStock = true;
    }
}
