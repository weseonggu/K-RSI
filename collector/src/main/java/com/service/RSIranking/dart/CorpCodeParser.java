package com.service.RSIranking.dart;

import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipInputStream;

public class CorpCodeParser {
    public Map<String, String> parse(InputStream zippedXml) {
        try (ZipInputStream zip = new ZipInputStream(zippedXml)) {
            if (zip.getNextEntry() == null) {
                throw new IllegalArgumentException("corpCode ZIP에 XML 파일이 없습니다.");
            }
            var factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            var document = factory.newDocumentBuilder().parse(new InputSource(zip));
            NodeList companies = document.getElementsByTagName("list");
            Map<String, String> result = new HashMap<>();
            for (int index = 0; index < companies.getLength(); index++) {
                Element company = (Element) companies.item(index);
                String stockCode = text(company, "stock_code").trim();
                if (!stockCode.isEmpty()) {
                    result.put(stockCode, text(company, "corp_code").trim());
                }
            }
            return result;
        } catch (Exception exception) {
            throw new IllegalArgumentException("DART 고유번호 파일 파싱 실패", exception);
        }
    }

    private String text(Element parent, String tagName) {
        return parent.getElementsByTagName(tagName).item(0).getTextContent();
    }
}
