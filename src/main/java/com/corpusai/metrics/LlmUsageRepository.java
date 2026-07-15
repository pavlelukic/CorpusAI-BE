package com.corpusai.metrics;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface LlmUsageRepository extends JpaRepository<LlmUsage, UUID> {

    String AGGREGATE_COLUMNS = """
            COUNT(*) AS calls,
            COALESCE(SUM(input_tokens), 0) AS totalInputTokens,
            COALESCE(SUM(output_tokens), 0) AS totalOutputTokens,
            COALESCE(SUM(total_tokens), 0) AS totalTokens,
            COALESCE(CAST(AVG(latency_ms) AS DOUBLE PRECISION), 0) AS avgLatencyMs,
            COALESCE(percentile_cont(0.95) WITHIN GROUP (ORDER BY latency_ms), 0) AS p95LatencyMs
            """;

    // Half-open interval [from, to): a row exactly on a shared boundary belongs to only one of
    // two adjacent windows (e.g. day N's "to" is day N+1's "from"), never both.
    String WHERE_RANGE = "WHERE created_at >= :from AND created_at < :to ";

    @Query(value = "SELECT CAST(NULL AS VARCHAR) AS groupKey, " + AGGREGATE_COLUMNS
            + "FROM llm_usage " + WHERE_RANGE,
            nativeQuery = true)
    UsageAggregateProjection aggregateOverall(@Param("from") Instant from, @Param("to") Instant to);

    @Query(value = "SELECT provider AS groupKey, " + AGGREGATE_COLUMNS
            + "FROM llm_usage " + WHERE_RANGE + "GROUP BY provider ORDER BY provider",
            nativeQuery = true)
    List<UsageAggregateProjection> aggregateByProvider(@Param("from") Instant from, @Param("to") Instant to);

    @Query(value = "SELECT model AS groupKey, " + AGGREGATE_COLUMNS
            + "FROM llm_usage " + WHERE_RANGE + "GROUP BY model ORDER BY model",
            nativeQuery = true)
    List<UsageAggregateProjection> aggregateByModel(@Param("from") Instant from, @Param("to") Instant to);

    @Query(value = "SELECT feature AS groupKey, " + AGGREGATE_COLUMNS
            + "FROM llm_usage " + WHERE_RANGE + "GROUP BY feature ORDER BY feature",
            nativeQuery = true)
    List<UsageAggregateProjection> aggregateByFeature(@Param("from") Instant from, @Param("to") Instant to);
}