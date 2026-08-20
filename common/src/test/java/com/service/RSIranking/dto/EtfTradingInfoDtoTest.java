package com.service.RSIranking.dto;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EtfTradingInfoDtoTest {
    @Test
    void mapsEtfOhlcvAndNormalizesNumbers() {
        EtfTradingInfoDto dto = EtfTradingInfoDto.fromJson(Map.ofEntries(
                Map.entry("BAS_DD", "20260819"), Map.entry("ISU_CD", "KR7069500007"), Map.entry("ISU_NM", "KODEX 200"),
                Map.entry("TDD_CLSPRC", "35,100"), Map.entry("CMPPREVDD_PRC", "100"), Map.entry("FLUC_RT", "0.29"),
                Map.entry("TDD_OPNPRC", "35,000"), Map.entry("TDD_HGPRC", "35,200"), Map.entry("TDD_LWPRC", "34,900"),
                Map.entry("ACC_TRDVOL", "1,234,567"), Map.entry("ACC_TRDVAL", "43,000,000,000")));

        assertThat(dto.getMktNm()).isEqualTo("ETF");
        assertThat(dto.getType()).isEqualTo("ETF");
        assertThat(dto.getTddClsprc()).isEqualTo("35100");
        assertThat(dto.getAccTrdvol()).isEqualTo("1234567");
        assertThat(dto.hasRequiredValues()).isTrue();
    }

    @Test
    void rejectsPlaceholderPriceInsteadOfConvertingItToZero() {
        EtfTradingInfoDto dto = EtfTradingInfoDto.fromJson(Map.ofEntries(
                Map.entry("BAS_DD", "20260819"), Map.entry("ISU_CD", "KR7069500007"), Map.entry("ISU_NM", "KODEX 200"),
                Map.entry("TDD_CLSPRC", "-"), Map.entry("CMPPREVDD_PRC", "0"), Map.entry("FLUC_RT", "0"),
                Map.entry("TDD_OPNPRC", "0"), Map.entry("TDD_HGPRC", "0"), Map.entry("TDD_LWPRC", "0"),
                Map.entry("ACC_TRDVOL", "0"), Map.entry("ACC_TRDVAL", "0")));

        assertThat(dto.getTddClsprc()).isNull();
        assertThat(dto.hasRequiredValues()).isFalse();
    }
}
