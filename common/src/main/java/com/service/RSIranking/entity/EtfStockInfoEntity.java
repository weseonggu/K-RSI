package com.service.RSIranking.entity;

import com.service.RSIranking.dto.StockDto;
import com.service.RSIranking.entity.inter.StockInfoEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Entity
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Builder
@Table(name = "etf_stock_info")
public class EtfStockInfoEntity implements StockInfoEntity {
    @Id
    @Column(name = "isu_cd", length = 100, nullable = false)
    private String id;
    @Column(name = "isu_nm", length = 100, nullable = false)
    private String isuNm;
    @Column(name = "mkt_nm", length = 20, nullable = false)
    private String mktNm;
    @Column(name = "is_public_stock", nullable = false)
    private Boolean isPublicStock;
    @OneToMany(mappedBy = "stock", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<EtfDailyTradingInformation> tradingInfo;

    public EtfStockInfoEntity(StockDto dto) {
        this.id = dto.getIsuCd();
        this.isuNm = dto.getIsuNm();
        this.mktNm = "ETF";
        this.isPublicStock = true;
    }

    @Override public void delistStock() { this.isPublicStock = false; }
    @Override public void updateFromDto(StockDto dto) {
        this.isuNm = dto.getIsuNm();
        this.mktNm = "ETF";
    }
}
