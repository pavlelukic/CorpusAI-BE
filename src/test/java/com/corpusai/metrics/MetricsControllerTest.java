package com.corpusai.metrics;

import com.corpusai.auth.Role;
import com.corpusai.auth.User;
import com.corpusai.auth.UserRepository;
import com.corpusai.model.ModelProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Real dev Postgres + per-test rollback (same convention as QuizControllerTest / AdminSubjectControllerTest).
// The dev DB already carries stray llm_usage rows from manual curl verification in earlier checkpoints,
// committed outside any test transaction, so every assertion-bearing test scopes its from/to window
// tightly around its own fixture rows' known timestamps rather than trusting an unfiltered count.
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MetricsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private LlmUsageRepository llmUsageRepository;

    // --- auth gate ---

    @Test
    void nonAdminCannotViewMetrics() throws Exception {
        String token = login(registerUser().getEmail());

        mockMvc.perform(get("/api/admin/metrics").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void nonAdminCannotExportMetrics() throws Exception {
        String token = login(registerUser().getEmail());

        mockMvc.perform(get("/api/admin/metrics/export").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    // --- overall totals & averages ---

    @Test
    void metricsReturnsOverallTotalsAndAverages() throws Exception {
        String adminToken = createAdminAndLogin();
        Instant base = Instant.now();
        seedFourRows(base);

        mockMvc.perform(get("/api/admin/metrics")
                        .param("from", base.minusSeconds(1).toString())
                        .param("to", base.plusSeconds(10).toString())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overall.calls").value(4))
                .andExpect(jsonPath("$.overall.totalInputTokens").value(100))
                .andExpect(jsonPath("$.overall.totalOutputTokens").value(50))
                .andExpect(jsonPath("$.overall.totalTokens").value(150))
                .andExpect(jsonPath("$.overall.avgLatencyMs").value(250.0))
                .andExpect(jsonPath("$.overall.p95LatencyMs").value(385.0))
                .andExpect(jsonPath("$.groups").isEmpty());
    }

    // --- groupBy ---

    @Test
    void metricsGroupsByProvider() throws Exception {
        String adminToken = createAdminAndLogin();
        Instant base = Instant.now();
        seedFourRows(base);

        mockMvc.perform(get("/api/admin/metrics")
                        .param("from", base.minusSeconds(1).toString())
                        .param("to", base.plusSeconds(10).toString())
                        .param("groupBy", "provider")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groups.length()").value(2))
                .andExpect(jsonPath("$.groups[0].key").value("ANTHROPIC"))
                .andExpect(jsonPath("$.groups[0].calls").value(2))
                .andExpect(jsonPath("$.groups[0].totalInputTokens").value(70))
                .andExpect(jsonPath("$.groups[0].avgLatencyMs").value(350.0))
                .andExpect(jsonPath("$.groups[0].p95LatencyMs").value(395.0))
                .andExpect(jsonPath("$.groups[1].key").value("OPENAI"))
                .andExpect(jsonPath("$.groups[1].calls").value(2))
                .andExpect(jsonPath("$.groups[1].totalInputTokens").value(30))
                .andExpect(jsonPath("$.groups[1].avgLatencyMs").value(150.0))
                .andExpect(jsonPath("$.groups[1].p95LatencyMs").value(195.0));
    }

    @Test
    void metricsGroupsByModel() throws Exception {
        String adminToken = createAdminAndLogin();
        Instant base = Instant.now();
        seedFourRows(base);

        mockMvc.perform(get("/api/admin/metrics")
                        .param("from", base.minusSeconds(1).toString())
                        .param("to", base.plusSeconds(10).toString())
                        .param("groupBy", "model")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groups.length()").value(2))
                .andExpect(jsonPath("$.groups[0].key").value("claude-haiku-4-5"))
                .andExpect(jsonPath("$.groups[0].calls").value(2))
                .andExpect(jsonPath("$.groups[0].avgLatencyMs").value(350.0))
                .andExpect(jsonPath("$.groups[1].key").value("gpt-4o-mini"))
                .andExpect(jsonPath("$.groups[1].calls").value(2))
                .andExpect(jsonPath("$.groups[1].avgLatencyMs").value(150.0));
    }

    @Test
    void metricsGroupsByFeature() throws Exception {
        String adminToken = createAdminAndLogin();
        Instant base = Instant.now();
        seedFourRows(base);

        mockMvc.perform(get("/api/admin/metrics")
                        .param("from", base.minusSeconds(1).toString())
                        .param("to", base.plusSeconds(10).toString())
                        .param("groupBy", "feature")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groups.length()").value(3))
                .andExpect(jsonPath("$.groups[0].key").value("CHAT"))
                .andExpect(jsonPath("$.groups[0].calls").value(2))
                .andExpect(jsonPath("$.groups[0].avgLatencyMs").value(150.0))
                .andExpect(jsonPath("$.groups[0].p95LatencyMs").value(195.0))
                .andExpect(jsonPath("$.groups[1].key").value("FLASHCARDS"))
                .andExpect(jsonPath("$.groups[1].calls").value(1))
                .andExpect(jsonPath("$.groups[1].avgLatencyMs").value(300.0))
                .andExpect(jsonPath("$.groups[1].p95LatencyMs").value(300.0))
                .andExpect(jsonPath("$.groups[2].key").value("QUIZ"))
                .andExpect(jsonPath("$.groups[2].calls").value(1))
                .andExpect(jsonPath("$.groups[2].avgLatencyMs").value(400.0));
    }

    // --- from/to filtering ---

    @Test
    void metricsFromToExcludesRowsOutsideWindow() throws Exception {
        String adminToken = createAdminAndLogin();
        Instant t = Instant.now();
        llmUsageRepository.save(new LlmUsage(LlmFeature.CHAT, ModelProvider.OPENAI, "gpt-4o-mini",
                1, 1, 2, 111L, null, null, null, null, t.minusSeconds(10)));
        llmUsageRepository.save(new LlmUsage(LlmFeature.CHAT, ModelProvider.OPENAI, "gpt-4o-mini",
                5, 5, 10, 555L, null, null, null, null, t));
        llmUsageRepository.save(new LlmUsage(LlmFeature.CHAT, ModelProvider.OPENAI, "gpt-4o-mini",
                9, 9, 18, 999L, null, null, null, null, t.plusSeconds(10)));

        mockMvc.perform(get("/api/admin/metrics")
                        .param("from", t.minusSeconds(5).toString())
                        .param("to", t.plusSeconds(5).toString())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overall.calls").value(1))
                .andExpect(jsonPath("$.overall.totalInputTokens").value(5))
                .andExpect(jsonPath("$.overall.avgLatencyMs").value(555.0));
    }

    @Test
    void metricsWithNoRowsInRangeReturnsZerosNotNulls() throws Exception {
        String adminToken = createAdminAndLogin();

        mockMvc.perform(get("/api/admin/metrics")
                        .param("from", "2099-01-01T00:00:00Z")
                        .param("to", "2099-01-02T00:00:00Z")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overall.calls").value(0))
                .andExpect(jsonPath("$.overall.totalInputTokens").value(0))
                .andExpect(jsonPath("$.overall.avgLatencyMs").value(0.0))
                .andExpect(jsonPath("$.overall.p95LatencyMs").value(0.0));
    }

    // --- invalid params ---

    @Test
    void metricsInvalidGroupByReturns400() throws Exception {
        String adminToken = createAdminAndLogin();

        mockMvc.perform(get("/api/admin/metrics")
                        .param("groupBy", "bogus")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"));
    }

    @Test
    void metricsInvalidFromReturns400() throws Exception {
        String adminToken = createAdminAndLogin();

        mockMvc.perform(get("/api/admin/metrics")
                        .param("from", "not-a-date")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"));
    }

    // --- CSV export ---

    @Test
    void exportReturnsCsvMatchingAggregation() throws Exception {
        String adminToken = createAdminAndLogin();
        Instant base = Instant.now();
        seedFourRows(base);

        String csv = mockMvc.perform(get("/api/admin/metrics/export")
                        .param("from", base.minusSeconds(1).toString())
                        .param("to", base.plusSeconds(10).toString())
                        .param("groupBy", "provider")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        List<String> lines = csv.lines().toList();
        assertThat(lines).containsExactly(
                "group,calls,totalInputTokens,totalOutputTokens,totalTokens,avgLatencyMs,p95LatencyMs",
                "OVERALL,4,100,50,150,250.0,385.0",
                "ANTHROPIC,2,70,35,105,350.0,395.0",
                "OPENAI,2,30,15,45,150.0,195.0");
    }

    // --- fixtures ---

    private void seedFourRows(Instant base) {
        llmUsageRepository.save(new LlmUsage(LlmFeature.CHAT, ModelProvider.OPENAI, "gpt-4o-mini",
                10, 5, 15, 100L, null, null, null, null, base));
        llmUsageRepository.save(new LlmUsage(LlmFeature.CHAT, ModelProvider.OPENAI, "gpt-4o-mini",
                20, 10, 30, 200L, null, null, null, null, base.plusSeconds(1)));
        llmUsageRepository.save(new LlmUsage(LlmFeature.FLASHCARDS, ModelProvider.ANTHROPIC, "claude-haiku-4-5",
                30, 15, 45, 300L, null, null, null, null, base.plusSeconds(2)));
        llmUsageRepository.save(new LlmUsage(LlmFeature.QUIZ, ModelProvider.ANTHROPIC, "claude-haiku-4-5",
                40, 20, 60, 400L, null, null, null, null, base.plusSeconds(3)));
    }

    private User registerUser() {
        String email = "test-" + UUID.randomUUID() + "@example.com";
        return userRepository.save(new User(email, passwordEncoder.encode("password123"), "Test User", Role.USER));
    }

    private String login(String email) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"password123"}
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("token").asText();
    }

    private String createAdminAndLogin() throws Exception {
        String email = "admin-" + UUID.randomUUID() + "@example.com";
        String password = "adminPass123";
        userRepository.save(new User(email, passwordEncoder.encode(password), "Test Admin", Role.ADMIN));
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("token").asText();
    }
}