package com.service.RSIranking.dart;

public class DartApiException extends RuntimeException {
    private final String status;

    public DartApiException(String status, String message) {
        super("DART API 오류 [" + status + "]: " + message);
        this.status = status;
    }

    public String getStatus() {
        return status;
    }
}
