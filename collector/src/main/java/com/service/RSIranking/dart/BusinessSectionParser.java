package com.service.RSIranking.dart;

import org.jsoup.Jsoup;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class BusinessSectionParser {
    private static final Pattern TITLE_PATTERN = Pattern.compile("(?is)<TITLE\\b[^>]*>(.*?)</TITLE>");
    public ParsedBusinessSection parse(InputStream zippedReport) {
        try (ZipInputStream zip = new ZipInputStream(zippedReport, Charset.forName("MS949"))) {
            ParsedBusinessSection best = null;
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                byte[] bytes = readEntry(zip);
                String source = decode(bytes);
                ParsedBusinessSection parsed = extract(source);
                if (parsed != null && (best == null || parsed.text().length() > best.text().length())) {
                    best = parsed;
                }
            }
            if (best == null) {
                throw new IllegalArgumentException("'II. 사업의 내용' 구간을 찾지 못했습니다.");
            }
            return best;
        } catch (DartApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("DART 사업보고서 ZIP 파싱 실패", exception);
        }
    }

    ParsedBusinessSection extract(String source) {
        Matcher matcher = TITLE_PATTERN.matcher(source);
        int start = -1;
        int end = source.length();
        while (matcher.find()) {
            String normalized = normalize(Jsoup.parse(matcher.group(1)).text());
            if (start < 0 && normalized.matches("(?i)^(II|Ⅱ|2)[.]?사업의내용.*")) {
                start = matcher.start();
            } else if (start >= 0 && normalized.matches("(?i)^(III|Ⅲ|3)[.]?.*")) {
                end = matcher.start();
                break;
            }
        }
        if (start < 0) return null;
        String text = Jsoup.parse(source.substring(start, end)).text()
                .replaceAll("[\\t\\x0B\\f\\r ]+", " ")
                .replaceAll(" *\\n+ *", "\\n").trim();
        return new ParsedBusinessSection(text, sha256(text));
    }

    private String normalize(String value) {
        return value.replaceAll("[\\s·]", "").replace('．', '.');
    }

    private String decode(byte[] bytes) {
        String asciiHead = new String(bytes, 0, Math.min(bytes.length, 300), StandardCharsets.ISO_8859_1);
        if (asciiHead.toLowerCase().contains("utf-8")) return new String(bytes, StandardCharsets.UTF_8);
        return new String(bytes, Charset.forName("MS949"));
    }

    private byte[] readEntry(InputStream input) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        input.transferTo(output);
        return output.toByteArray();
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    public record ParsedBusinessSection(String text, String hash) {
    }
}
