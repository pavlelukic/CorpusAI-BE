package com.corpusai.metrics;

import com.corpusai.model.ModelProvider;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// Proves LlmUsage round-trips against Postgres, including the fully-nullable
// user/subject/session/token columns the QUERY_COMPRESSION feature relies on.
@SpringBootTest
@Transactional
class LlmUsagePersistenceTest {

    private static final String SUBJECT_ID = "softverski-proces";

    @Autowired
    private LlmUsageRepository llmUsageRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void fullyPopulatedRowSurvivesRoundTrip() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();

        LlmUsage usage = llmUsageRepository.save(new LlmUsage(
                LlmFeature.CHAT, ModelProvider.ANTHROPIC, "claude-haiku-4-5",
                120, 45, 165, 842L, userId, SUBJECT_ID, sessionId, Instant.now()));

        entityManager.flush();
        entityManager.clear();

        LlmUsage reloaded = llmUsageRepository.findById(usage.getId()).orElseThrow();

        assertThat(reloaded.getFeature()).isEqualTo(LlmFeature.CHAT);
        assertThat(reloaded.getProvider()).isEqualTo(ModelProvider.ANTHROPIC);
        assertThat(reloaded.getModel()).isEqualTo("claude-haiku-4-5");
        assertThat(reloaded.getInputTokens()).isEqualTo(120);
        assertThat(reloaded.getOutputTokens()).isEqualTo(45);
        assertThat(reloaded.getTotalTokens()).isEqualTo(165);
        assertThat(reloaded.getLatencyMs()).isEqualTo(842L);
        assertThat(reloaded.getUserId()).isEqualTo(userId);
        assertThat(reloaded.getSubjectId()).isEqualTo(SUBJECT_ID);
        assertThat(reloaded.getSessionId()).isEqualTo(sessionId);
        assertThat(reloaded.getCreatedAt()).isNotNull();
    }

    @Test
    void queryCompressionRowWithNoContextSurvivesRoundTrip() {
        LlmUsage usage = llmUsageRepository.save(new LlmUsage(
                LlmFeature.QUERY_COMPRESSION, ModelProvider.OPENAI, "gpt-4o-mini",
                null, null, null, 311L, null, null, null, Instant.now()));

        entityManager.flush();
        entityManager.clear();

        LlmUsage reloaded = llmUsageRepository.findById(usage.getId()).orElseThrow();

        assertThat(reloaded.getFeature()).isEqualTo(LlmFeature.QUERY_COMPRESSION);
        assertThat(reloaded.getProvider()).isEqualTo(ModelProvider.OPENAI);
        assertThat(reloaded.getModel()).isEqualTo("gpt-4o-mini");
        assertThat(reloaded.getInputTokens()).isNull();
        assertThat(reloaded.getOutputTokens()).isNull();
        assertThat(reloaded.getTotalTokens()).isNull();
        assertThat(reloaded.getLatencyMs()).isEqualTo(311L);
        assertThat(reloaded.getUserId()).isNull();
        assertThat(reloaded.getSubjectId()).isNull();
        assertThat(reloaded.getSessionId()).isNull();
    }
}