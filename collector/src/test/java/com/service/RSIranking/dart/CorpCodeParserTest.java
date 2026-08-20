package com.service.RSIranking.dart;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class CorpCodeParserTest {
    @Test
    void mapsOnlyListedCompanies() throws Exception {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <result>
                  <list><corp_code>00126380</corp_code><corp_name>삼성전자</corp_name><stock_code>005930</stock_code></list>
                  <list><corp_code>00000001</corp_code><corp_name>비상장</corp_name><stock_code> </stock_code></list>
                </result>
                """;

        assertThat(new CorpCodeParser().parse(zip("CORPCODE.xml", xml)))
                .hasSize(1)
                .containsEntry("005930", "00126380");
    }

    private ByteArrayInputStream zip(String name, String value) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            zip.putNextEntry(new ZipEntry(name));
            zip.write(value.getBytes(StandardCharsets.UTF_8));
        }
        return new ByteArrayInputStream(output.toByteArray());
    }
}
