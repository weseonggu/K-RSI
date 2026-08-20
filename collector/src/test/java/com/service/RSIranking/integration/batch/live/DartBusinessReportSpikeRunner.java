package com.service.RSIranking.integration.batch.live;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.service.RSIranking.dart.BusinessSectionParser;
import com.service.RSIranking.dart.DartBusinessReport;
import com.service.RSIranking.dart.DartBusinessReportService;
import com.service.RSIranking.dart.DartClient;
import com.service.RSIranking.dart.DartApiException;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

class DartBusinessReportSpikeRunner {
    private static final List<String> STOCK_CODES = List.of(
            "005930", "035420", "005380", "454910", "277810",
            "068270", "105560", "402340", "365550", "042700");

    @Test
    void collectTenBusinessReports() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("dart.live.run"), "-Ddart.live.run=true 일 때만 실행");
        String apiKey = loadApiKey();
        Path outputDir = Path.of(System.getProperty("dart.live.outputDir", "build/dart-spike"));
        Files.createDirectories(outputDir);

        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        DartBusinessReportService service = new DartBusinessReportService(new DartClient(apiKey), new BusinessSectionParser());
        List<Map<String, Object>> summary = new ArrayList<>();

        for (String stockCode : STOCK_CODES) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("stockCode", stockCode);
            try {
                DartBusinessReport result = service.collect(stockCode);
                mapper.writeValue(outputDir.resolve(stockCode + ".json").toFile(), result);
                item.put("status", "SUCCESS");
                item.put("textLength", result.businessText().length());
                item.put("receiptNumber", result.receiptNumber());
            } catch (Exception exception) {
                item.put("status", "FAILED");
                item.put("errorType", exception.getClass().getSimpleName());
                item.put("message", exception.getMessage());
                summary.add(item);
                if (exception instanceof DartApiException apiException
                        && ("020".equals(apiException.getStatus()) || "800".equals(apiException.getStatus()))) {
                    break;
                }
                continue;
            }
            summary.add(item);
        }
        mapper.writeValue(outputDir.resolve("summary.json").toFile(), summary);
    }

    private String loadApiKey() throws Exception {
        String environment = System.getenv("DART_API_KEY");
        if (environment != null && !environment.isBlank()) return environment;
        for (String line : Files.readAllLines(Path.of("..", ".env"), StandardCharsets.UTF_8)) {
            if (line.startsWith("DART_API_KEY=")) return line.substring("DART_API_KEY=".length()).trim();
        }
        throw new IllegalStateException("DART_API_KEY를 환경변수 또는 루트 .env에 설정하세요.");
    }
}
