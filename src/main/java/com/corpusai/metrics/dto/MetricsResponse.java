package com.corpusai.metrics.dto;

import java.util.List;

public record MetricsResponse(UsageTotalsResponse overall, List<UsageGroupResponse> groups) {
}
