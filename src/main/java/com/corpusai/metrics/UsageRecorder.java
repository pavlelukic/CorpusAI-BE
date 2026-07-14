package com.corpusai.metrics;

import com.corpusai.model.ModelProvider;
import dev.langchain4j.model.output.TokenUsage;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class UsageRecorder {

    private final LlmUsageRepository llmUsageRepository;

    public UsageRecorder(LlmUsageRepository llmUsageRepository) {
        this.llmUsageRepository = llmUsageRepository;
    }

    public void record(LlmFeature feature, ModelProvider provider, String model, TokenUsage tokenUsage,
                        long latencyMs, UUID userId, String subjectId, UUID sessionId) {
        Integer inputTokens = tokenUsage != null ? tokenUsage.inputTokenCount() : null;
        Integer outputTokens = tokenUsage != null ? tokenUsage.outputTokenCount() : null;
        Integer totalTokens = tokenUsage != null ? tokenUsage.totalTokenCount() : null;

        llmUsageRepository.save(new LlmUsage(feature, provider, model, inputTokens, outputTokens,
                totalTokens, latencyMs, userId, subjectId, sessionId));
    }
}