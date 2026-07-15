package com.corpusai.metrics;

public enum GroupBy {
    PROVIDER,
    MODEL,
    FEATURE;

    public static GroupBy fromParam(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return GroupBy.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid groupBy: " + raw + " (expected provider, model, or feature)");
        }
    }
}