package com.service.RSIranking.dart;

import java.io.ByteArrayInputStream;
import java.util.List;

public class DartBusinessReportService {
    private final DartClient client;
    private final BusinessSectionParser parser;

    public DartBusinessReportService(DartClient client, BusinessSectionParser parser) {
        this.client = client;
        this.parser = parser;
    }

    public DartBusinessReport collect(String stockCode) {
        String corpCode = client.findCorpCode(stockCode);
        DartDisclosure disclosure = client.findLatestAnnualReport(corpCode);
        var section = parser.parse(new ByteArrayInputStream(client.downloadReport(disclosure.receiptNumber())));
        return new DartBusinessReport(stockCode, corpCode, disclosure.receiptNumber(), disclosure.reportName(),
                section.text(), section.hash(), "SUCCESS", List.of());
    }
}
