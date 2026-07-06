package com.service.RSIranking.entity;

import com.service.RSIranking.dto.StockDto;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 종목 정보 엔티티 (범용).
 *
 * <p>주식 종목의 기본 정보를 저장하는 JPA 엔티티입니다.</p>
 *
 * <h2>저장 정보</h2>
 * <ul>
 *   <li>종목 코드 (Primary Key)</li>
 *   <li>종목명</li>
 *   <li>시장 구분</li>
 *   <li>상장 여부</li>
 * </ul>
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
        name = "stock_info"
)
public class StockInfoEntity {
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
    private List<DailyTradingInformation> tradingInfo;

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
    public StockInfoEntity(StockDto dto) {
        this.id = dto.getIsuCd();
        this.isuNm = dto.getIsuNm();
        this.mktNm = dto.getMktNm();
        this.isPublicStock = true;
    }
}
