package com.corpusai.metrics.dto;

public record UsageTotalsResponse(long calls, long totalInputTokens, long totalOutputTokens,
                                   long totalTokens, double avgLatencyMs, double p95LatencyMs) {
}