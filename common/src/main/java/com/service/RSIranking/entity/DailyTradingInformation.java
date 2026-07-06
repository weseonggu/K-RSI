package com.service.RSIranking.entity;

import com.service.RSIranking.dto.TradingInfoDto;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 일별 매매 정보 엔티티 (범용).
 *
 * <p>주식의 일별 매매 정보를 저장하는 JPA 엔티티입니다.
 * 날짜와 종목코드의 조합이 유일합니다.</p>
 *
 * <h2>저장 정보</h2>
 * <ul>
 *   <li>거래일자</li>
 *   <li>종가/시가/고가/저가</li>
 *   <li>대비/등락률</li>
 *   <li>거래량/거래대금</li>
 *   <li>RSI 지표 (평균상승폭/평균하락폭 포함)</li>
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
        name = "daily_trading_information",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"date", "isu_cd"})
        }
)
public class DailyTradingInformation implements com.service.RSIranking.entity.inter.DailyTradingInformation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;// 기본키

    @Column(name = "date")
    private LocalDate date;// 날짜

    @Column(name = "tdd_clsprc")
    private Integer tddClsprc;// 종가

    @Column(name = "cmpprevdd_prc")
    private Integer cmpprevddPrc;// 대비

    @Column(name = "fluc_rt")
    private Double flucRt;// 등략률

    @Column(name = "tdd_opnprc")
    private Integer tddOpnprc;// 시가

    @Column(name = "tdd_hgprc")
    private Integer tddHgprc;// 고가

    @Column(name = "tdd_lwprc")
    private Integer tddLwprc;// 저가

    @Column(name = "rsi")
    private Double rsi;// rsi 지표

    @Column(name = "acc_trdvol")
    private Long accTrdvol;// 거래량

    @Column(name = "acc_trdval")
    private Long accTrdval;// 거래 대금

    @Column(name = "avg_closing_gain")
    private Double avgClosingGain;

    @Column(name = "avg_closing_loss")
    private Double avgClosingLoss;

    //================================================================

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "isu_cd")
    private StockInfoEntity stock;

    //================================================================

    // dto -> entity로 변경하기
    public DailyTradingInformation(TradingInfoDto dto){
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd");
        this.date = LocalDate.parse(dto.getBasDd(),formatter);
        this.tddClsprc = Integer.parseInt(dto.getTddClsprc());
        this.cmpprevddPrc = Integer.parseInt(dto.getCmpprevddPrc());
        this.flucRt = Double.parseDouble(dto.getFlucRt());
        this.tddOpnprc = Integer.parseInt(dto.getTddOpnprc());
        this.tddHgprc = Integer.parseInt(dto.getTddHgprc());
        this.tddLwprc = Integer.parseInt(dto.getTddLwprc());
        this.accTrdvol = Long.parseLong(dto.getAccTrdvol());
        this.accTrdval =  Long.parseLong(dto.getAccTrdval());
    }
    public void updateRSIInfo(Double Ag, Double Al, Double RSI){
        this.avgClosingGain = Ag;
        this.avgClosingLoss = Al;
        this.rsi = RSI;
    }
}
