package com.service.RSIranking.dart;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;

public class DartClient {
    private static final String BASE_URL = "https://opendart.fss.or.kr/api/";
    private static final long MIN_REQUEST_INTERVAL_MILLIS = 1_000;

    private final String apiKey;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final CorpCodeParser corpCodeParser;
    private long lastRequestAt;
    private Map<String, String> corpCodes;

    public DartClient(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) throw new IllegalArgumentException("DART_API_KEY가 필요합니다.");
        this.apiKey = apiKey;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        this.objectMapper = new ObjectMapper();
        this.corpCodeParser = new CorpCodeParser();
    }

    public String findCorpCode(String stockCode) {
        if (corpCodes == null) {
            corpCodes = corpCodeParser.parse(new ByteArrayInputStream(getBytes("corpCode.xml")));
        }
        String corpCode = corpCodes.get(stockCode);
        if (corpCode == null) throw new IllegalArgumentException("DART 고유번호에서 종목코드를 찾지 못했습니다: " + stockCode);
        return corpCode;
    }

    public DartDisclosure findLatestAnnualReport(String corpCode) {
        LocalDate today = LocalDate.now();
        String query = "list.json?corp_code=" + encode(corpCode)
                + "&bgn_de=" + today.minusYears(2).format(DateTimeFormatter.BASIC_ISO_DATE)
                + "&end_de=" + today.format(DateTimeFormatter.BASIC_ISO_DATE)
                + "&pblntf_ty=A&pblntf_detail_ty=A001&page_count=100";
        JsonNode root = readJson(getBytes(query));
        DartDisclosure latest = null;
        for (JsonNode node : root.path("list")) {
            if (!node.path("report_nm").asText().matches("^사업보고서.*")) continue;
            DartDisclosure candidate = new DartDisclosure(node.path("rcept_no").asText(),
                    node.path("report_nm").asText(), node.path("rcept_dt").asText());
            if (latest == null || candidate.receiptDate().compareTo(latest.receiptDate()) > 0) latest = candidate;
        }
        if (latest == null) throw new IllegalArgumentException("최근 2년 사업보고서가 없습니다: " + corpCode);
        return latest;
    }

    public byte[] downloadReport(String receiptNumber) {
        return getBytes("document.xml?rcept_no=" + encode(receiptNumber));
    }

    private byte[] getBytes(String endpoint) {
        throttle();
        String delimiter = endpoint.contains("?") ? "&" : "?";
        URI uri = URI.create(BASE_URL + endpoint + delimiter + "crtfc_key=" + encode(apiKey));
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(90)).GET().build();
        try {
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() / 100 != 2) {
                throw new DartApiException(Integer.toString(response.statusCode()), "HTTP 요청 실패");
            }
            detectApiError(response.body());
            return response.body();
        } catch (DartApiException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("DART API 호출 중 중단됨", exception);
        } catch (Exception exception) {
            throw new IllegalStateException("DART API 호출 실패", exception);
        }
    }

    private JsonNode readJson(byte[] bytes) {
        try {
            JsonNode root = objectMapper.readTree(bytes);
            String status = root.path("status").asText();
            if (!"000".equals(status)) throw new DartApiException(status, root.path("message").asText());
            return root;
        } catch (DartApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("DART JSON 응답 파싱 실패", exception);
        }
    }

    private void detectApiError(byte[] body) {
        if (body.length > 2_000) return;
        String text = new String(body, StandardCharsets.UTF_8).trim();
        if (!text.startsWith("<?xml") && !text.startsWith("<result")) return;
        String status = between(text, "<status>", "</status>");
        String message = between(text, "<message>", "</message>");
        if (status != null && !"000".equals(status)) throw new DartApiException(status, message);
    }

    private String between(String value, String start, String end) {
        int from = value.indexOf(start);
        if (from < 0) return null;
        int to = value.indexOf(end, from + start.length());
        return to < 0 ? null : value.substring(from + start.length(), to).trim();
    }

    private synchronized void throttle() {
        long wait = MIN_REQUEST_INTERVAL_MILLIS - (System.currentTimeMillis() - lastRequestAt);
        if (wait > 0) {
            try {
                Thread.sleep(wait);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("DART 호출 대기 중 중단됨", exception);
            }
        }
        lastRequestAt = System.currentTimeMillis();
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
