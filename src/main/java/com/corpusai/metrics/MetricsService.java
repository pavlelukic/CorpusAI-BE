package com.corpusai.metrics;

import org.springframework.stereotype.Service;

import java.time.DateTimeException;
import java.time.Instant;
import java.util.List;

@Service
public class MetricsService {

    private final LlmUsageRepository llmUsageRepository;

    public MetricsService(LlmUsageRepository llmUsageRepository) {
        this.llmUsageRepository = llmUsageRepository;
    }

    public MetricsResult aggregate(String fromParam, String toParam, String groupByParam) {
        Instant from = parseInstant(fromParam, Instant.EPOCH, "from");
        Instant to = parseInstant(toParam, Instant.now(), "to");
        GroupBy groupBy = GroupBy.fromParam(groupByParam);

        UsageAggregateProjection overall = llmUsageRepository.aggregateOverall(from, to);
        List<UsageAggregateProjection> groups = switch (groupBy) {
            case null -> List.of();
            case PROVIDER -> llmUsageRepository.aggregateByProvider(from, to);
            case MODEL -> llmUsageRepository.aggregateByModel(from, to);
            case FEATURE -> llmUsageRepository.aggregateByFeature(from, to);
        };

        return new MetricsResult(overall, groups);
    }

    private Instant parseInstant(String raw, Instant defaultValue, String paramName) {
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        try {
            return Instant.parse(raw);
        } catch (DateTimeException ex) {
            throw new IllegalArgumentException(
                    "Invalid " + paramName + ": " + raw + " (expected ISO-8601 instant, e.g. 2026-07-01T00:00:00Z)");
        }
    }

    public record MetricsResult(UsageAggregateProjection overall, List<UsageAggregateProjection> groups) {
    }
}