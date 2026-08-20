package com.service.RSIranking.entity;

import com.service.RSIranking.dto.TradingInfoDto;
import com.service.RSIranking.entity.inter.DailyTradingInformation;
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
@Table(name = "etf_daily_trading_information",
        uniqueConstraints = @UniqueConstraint(columnNames = {"date", "isu_cd"}))
public class EtfDailyTradingInformation implements DailyTradingInformation {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "date") private LocalDate date;
    @Column(name = "tdd_clsprc") private Integer tddClsprc;
    @Column(name = "cmpprevdd_prc") private Integer cmpprevddPrc;
    @Column(name = "fluc_rt") private Double flucRt;
    @Column(name = "tdd_opnprc") private Integer tddOpnprc;
    @Column(name = "tdd_hgprc") private Integer tddHgprc;
    @Column(name = "tdd_lwprc") private Integer tddLwprc;
    @Column(name = "rsi") private Double rsi;
    @Column(name = "acc_trdvol") private Long accTrdvol;
    @Column(name = "acc_trdval") private Long accTrdval;
    @Column(name = "avg_closing_gain") private Double avgClosingGain;
    @Column(name = "avg_closing_loss") private Double avgClosingLoss;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "isu_cd") private EtfStockInfoEntity stock;

    public EtfDailyTradingInformation(TradingInfoDto dto) {
        this.date = LocalDate.parse(dto.getBasDd(), DateTimeFormatter.ofPattern("yyyyMMdd"));
        this.tddClsprc = Integer.parseInt(dto.getTddClsprc());
        this.cmpprevddPrc = Integer.parseInt(dto.getCmpprevddPrc());
        this.flucRt = Double.parseDouble(dto.getFlucRt());
        this.tddOpnprc = Integer.parseInt(dto.getTddOpnprc());
        this.tddHgprc = Integer.parseInt(dto.getTddHgprc());
        this.tddLwprc = Integer.parseInt(dto.getTddLwprc());
        this.accTrdvol = Long.parseLong(dto.getAccTrdvol());
        this.accTrdval = Long.parseLong(dto.getAccTrdval());
    }

    @Override public void updateRSIInfo(Double ag, Double al, Double rsi) {
        this.avgClosingGain = ag; this.avgClosingLoss = al; this.rsi = rsi;
    }
}
