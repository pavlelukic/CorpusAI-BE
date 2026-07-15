package com.corpusai.metrics.dto;

public record UsageGroupResponse(String key, long calls, long totalInputTokens, long totalOutputTokens,
                                  long totalTokens, double avgLatencyMs, double p95LatencyMs) {
}
