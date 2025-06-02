package com.service.RSIranking.entity;

import com.service.RSIranking.dto.TradingInfoDto;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Entity
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Builder
public class DailyTradingInformation {

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
    private Long accTedval;// 거래 대금

    //================================================================

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "isu_cd")
    private SecuritiesStockEntity stock;

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
        this.accTedval =  Long.parseLong(dto.getAccTrdval());
    }
}
