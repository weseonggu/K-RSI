package com.service.RSIranking.dart;

import java.util.List;

public record DartBusinessReport(
        String stockCode,
        String corpCode,
        String receiptNumber,
        String reportName,
        String businessText,
        String businessHash,
        String status,
        List<String> warnings) {
}
