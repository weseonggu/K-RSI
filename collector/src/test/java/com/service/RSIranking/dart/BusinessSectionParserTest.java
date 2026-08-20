package com.service.RSIranking.dart;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BusinessSectionParserTest {
    @Test
    void extractsBusinessSectionAndStopsAtNextMajorSection() throws Exception {
        String xml = """
                <?xml version="1.0" encoding="EUC-KR"?>
                <DOCUMENT>
                  <TITLE>Ⅰ. 회사의 개요</TITLE><P>회사 소개</P>
                  <TITLE>Ⅱ. 사업의 내용</TITLE><P>반도체와 로봇 부품을 제조합니다.</P><P>주요 제품은 감속기입니다.</P>
                  <TITLE>Ⅲ. 재무에 관한 사항</TITLE><P>포함되면 안 되는 재무 문장</P>
                </DOCUMENT>
                """;

        var result = new BusinessSectionParser().parse(zip(xml, Charset.forName("MS949")));

        assertThat(result.text()).contains("사업의 내용", "반도체와 로봇 부품", "주요 제품은 감속기")
                .doesNotContain("재무 문장");
        assertThat(result.hash()).hasSize(64);
        assertThat(new BusinessSectionParser().parse(zip(xml, Charset.forName("MS949"))).hash())
                .isEqualTo(result.hash());
    }

    @Test
    void reportsMissingBusinessSection() throws Exception {
        assertThatThrownBy(() -> new BusinessSectionParser().parse(zip("<TITLE>Ⅰ. 회사의 개요</TITLE>", Charset.forName("MS949"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ZIP 파싱 실패");
    }

    private ByteArrayInputStream zip(String value, Charset charset) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output, Charset.forName("MS949"))) {
            zip.putNextEntry(new ZipEntry("report.xml"));
            zip.write(value.getBytes(charset));
        }
        return new ByteArrayInputStream(output.toByteArray());
    }
}
