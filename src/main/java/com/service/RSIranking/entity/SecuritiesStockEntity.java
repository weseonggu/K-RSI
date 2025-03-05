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
@Builder(access = AccessLevel.PRIVATE)
public class SecuritiesStockEntity {
    @Id
    @Column(name = "isu_cd", length = 20, nullable = false)
    private String isuCd;  // 표준코드

    @Column(name = "isu_srt_cd", length = 20, nullable = false)
    private String isuSrtCd;  // 단축코드

    @Column(name = "isu_nm", length = 100, nullable = false)
    private String isuNm;  // 한글 종목명

    @Column(name = "isu_abbrv", length = 50)
    private String isuAbbrv;  // 한글 종목약명

    @Column(name = "isu_eng_nm", length = 100)
    private String isuEngNm;  // 영문 종목명

    @Column(name = "list_dd", length = 10)
    private String listDd;  // 상장일 (YYYY-MM-DD)

    @Column(name = "mkt_tp_nm", length = 20)
    private String mktTpNm;  // 시장구분 (KOSPI, KOSDAQ 등)

    @Column(name = "secugrp_nm", length = 50)
    private String secugrpNm;  // 증권구분

    @Column(name = "sect_tp_nm", length = 50)
    private String sectTpNm;  // 소속부

    @Column(name = "kind_stkcert_tp_nm", length = 50)
    private String kindStkcertTpNm;  // 주식종류

    @Column(name = "parval", length = 20)
    private String parval;  // 액면가

    @Column(name = "list_shrs", length = 20)
    private String listShrs;  // 상장주식수

    // json 응답 엔티티로 파싱
    public static SecuritiesStockEntity parseJsonToSecuritiesStock(Map<String, Object> stock){
        return SecuritiesStockEntity.builder()
                .isuCd((String) stock.getOrDefault("ISU_CD", "N/A"))
                .isuSrtCd((String) stock.getOrDefault("isuSrtCd", "N/A"))
                .isuNm((String) stock.getOrDefault("isuNm", "N/A"))
                .isuAbbrv((String) stock.getOrDefault("isuAbbrv", "N/A"))
                .isuEngNm((String) stock.getOrDefault("isuEngNm", "N/A"))
                .listDd((String) stock.getOrDefault("listDd", "N/A"))
                .mktTpNm((String) stock.getOrDefault("mktTpNm", "N/A"))
                .secugrpNm((String) stock.getOrDefault("secugrpNm", "N/A"))
                .sectTpNm((String) stock.getOrDefault("sectTpNm", "N/A"))
                .kindStkcertTpNm((String) stock.getOrDefault("kindStkcertTpNm", "N/A"))
                .parval((String) stock.getOrDefault("parval", "N/A"))
                .listShrs((String) stock.getOrDefault("listShrs", "N/A"))
                .build();
    }
}
