package com.corpusai.metrics;

import com.corpusai.model.ModelProvider;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "llm_usage")
public class LlmUsage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LlmFeature feature;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ModelProvider provider;

    @Column(nullable = false)
    private String model;

    @Column(name = "input_tokens")
    private Integer inputTokens;

    @Column(name = "output_tokens")
    private Integer outputTokens;

    @Column(name = "total_tokens")
    private Integer totalTokens;

    @Column(name = "latency_ms", nullable = false)
    private long latencyMs;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "subject_id")
    private String subjectId;

    @Column(name = "session_id")
    private UUID sessionId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected LlmUsage() {
        // required by Hibernate
    }

    public LlmUsage(LlmFeature feature, ModelProvider provider, String model, Integer inputTokens,
                     Integer outputTokens, Integer totalTokens, long latencyMs, UUID userId,
                     String subjectId, UUID sessionId, Instant createdAt) {
        this.feature = feature;
        this.provider = provider;
        this.model = model;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.totalTokens = totalTokens;
        this.latencyMs = latencyMs;
        this.userId = userId;
        this.subjectId = subjectId;
        this.sessionId = sessionId;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public LlmFeature getFeature() {
        return feature;
    }

    public ModelProvider getProvider() {
        return provider;
    }

    public String getModel() {
        return model;
    }

    public Integer getInputTokens() {
        return inputTokens;
    }

    public Integer getOutputTokens() {
        return outputTokens;
    }

    public Integer getTotalTokens() {
        return totalTokens;
    }

    public long getLatencyMs() {
        return latencyMs;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getSubjectId() {
        return subjectId;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}