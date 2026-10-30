package com.example.activation.observability;

import org.slf4j.MDC;

import java.util.UUID;

public final class CorrelationId {

    public static final String HEADER = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";

    private CorrelationId() {
    }

    public static String current() {
        return MDC.get(MDC_KEY);
    }

    public static String generate() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    public static MDC.MDCCloseable bind(String value) {
        return MDC.putCloseable(MDC_KEY, value != null ? value : generate());
    }
}
