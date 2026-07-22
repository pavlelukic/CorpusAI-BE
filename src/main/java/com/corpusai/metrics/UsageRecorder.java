package com.corpusai.metrics;

import com.corpusai.model.ModelProvider;
import dev.langchain4j.model.output.TokenUsage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Component
public class UsageRecorder {

    private final LlmUsageRepository llmUsageRepository;

    public UsageRecorder(LlmUsageRepository llmUsageRepository) {
        this.llmUsageRepository = llmUsageRepository;
    }

    // For features that produce no chat message of their own - flashcards, quizzes, query compression.
    public void record(LlmFeature feature, ModelProvider provider, String model, TokenUsage tokenUsage,
                        long latencyMs, UUID userId, String subjectId, UUID sessionId) {
        record(feature, provider, model, tokenUsage, latencyMs, userId, subjectId, sessionId, null);
    }

    // REQUIRES_NEW + swallowed exceptions: a metrics-write failure must never roll back or fail
    // the caller's own transaction/request (chat, flashcards, quiz, query compression) - it should
    // degrade to "one row missing" rather than "feature broken".
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(LlmFeature feature, ModelProvider provider, String model, TokenUsage tokenUsage,
                        long latencyMs, UUID userId, String subjectId, UUID sessionId, UUID messageId) {
        try {
            Integer inputTokens = tokenUsage != null ? tokenUsage.inputTokenCount() : null;
            Integer outputTokens = tokenUsage != null ? tokenUsage.outputTokenCount() : null;
            Integer totalTokens = tokenUsage != null ? tokenUsage.totalTokenCount() : null;

            llmUsageRepository.save(new LlmUsage(feature, provider, model, inputTokens, outputTokens,
                    totalTokens, latencyMs, userId, subjectId, sessionId, messageId, Instant.now()));
        } catch (Exception ex) {
            log.error("Failed to record LLM usage (feature={}, provider={}, model={}) - continuing without it",
                    feature, provider, model, ex);
        }
    }
}