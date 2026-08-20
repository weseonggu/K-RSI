package com.service.RSIranking.dto;

import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Map;

/** ETF 일별 매매 정보를 기존 RSI 파이프라인 형식으로 변환한 DTO. */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonTypeName("ETF")
public class EtfTradingInfoDto implements Serializable, TradingInfoDto {
    private String basDd;
    private String isuCd;
    private String isuNm;
    private String mktNm;
    private String tddClsprc;
    private String cmpprevddPrc;
    private String flucRt;
    private String tddOpnprc;
    private String tddHgprc;
    private String tddLwprc;
    private String accTrdvol;
    private String accTrdval;
    private String type;

    public static EtfTradingInfoDto fromJson(Map<String, Object> stock) {
        return EtfTradingInfoDto.builder()
                .basDd(value(stock, "BAS_DD"))
                .isuCd(value(stock, "ISU_CD"))
                .isuNm(value(stock, "ISU_NM"))
                .mktNm("ETF")
                .tddClsprc(number(stock, "TDD_CLSPRC"))
                .cmpprevddPrc(number(stock, "CMPPREVDD_PRC"))
                .flucRt(number(stock, "FLUC_RT"))
                .tddOpnprc(number(stock, "TDD_OPNPRC"))
                .tddHgprc(number(stock, "TDD_HGPRC"))
                .tddLwprc(number(stock, "TDD_LWPRC"))
                .accTrdvol(number(stock, "ACC_TRDVOL"))
                .accTrdval(number(stock, "ACC_TRDVAL"))
                .type("ETF")
                .build();
    }

    public boolean hasRequiredValues() {
        return present(basDd) && present(isuCd) && present(tddClsprc)
                && present(cmpprevddPrc) && present(flucRt)
                && present(tddOpnprc) && present(tddHgprc) && present(tddLwprc)
                && present(accTrdvol) && present(accTrdval);
    }

    private static String value(Map<String, Object> source, String key) {
        Object value = source.get(key);
        return value == null ? null : value.toString().trim();
    }

    private static String number(Map<String, Object> source, String key) {
        String value = value(source, key);
        if (!present(value) || "-".equals(value)) {
            return null;
        }
        return value.replace(",", "");
    }

    private static boolean present(String value) {
        return value != null && !value.isBlank() && !"-".equals(value);
    }
}
