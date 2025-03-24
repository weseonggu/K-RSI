package com.service.RSIranking.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Builder
@SequenceGenerator(name = "DAILY_TRADING_INFO_SEQ", sequenceName = "TRADING_INFO_SEQ",initialValue = 1, allocationSize = 1)
public class DailyTradingInformation {

    @Id
    @GeneratedValue(strategy = GenerationType. SEQUENCE, generator = "DAILY_TRADING_INFO_SEQ")
    private Long id;// 기본키

    @Column(name = "date")
    private LocalDate date;// 날짜

    @Column(name = "tdd_clsprc")
    private int tddClsprc;// 종가

    @Column(name = "cmpprevdd_prc")
    private int cmpprevddPrc;// 대비

    @Column(name = "fluc_rt")
    private double flucRt;// 등략률

    @Column(name = "tdd_opnprc")
    private int tddOpnprc;// 시가

    @Column(name = "tdd_hgprc")
    private int tddHgprc;// 고가

    @Column(name = "tdd_lwprc")
    private int tddLwprc;// 저가

    @Column(name = "rsi")
    private double rsi;// rsi 지표

    @Column(name = "acc_trdvol")
    private int accTrdvol;// 거래량

    @Column(name = "acc_redval")
    private int accRedval;// 거래 대금

    //================================================================

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "isu_cd")
    private SecuritiesStockEntity stock;

    //================================================================

}
