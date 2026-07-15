package com.corpusai.metrics;

// Shared shape for all four native aggregation queries in LlmUsageRepository. getGroupKey() is
// null for the ungrouped/overall query and the grouped value (provider/model/feature) otherwise.
public interface UsageAggregateProjection {

    String getGroupKey();

    Long getCalls();

    Long getTotalInputTokens();

    Long getTotalOutputTokens();

    Long getTotalTokens();

    Double getAvgLatencyMs();

    Double getP95LatencyMs();
}