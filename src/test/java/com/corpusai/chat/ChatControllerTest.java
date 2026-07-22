package com.corpusai.chat;

import com.corpusai.auth.Role;
import com.corpusai.auth.User;
import com.corpusai.auth.UserRepository;
import com.corpusai.auth.UserSubjectAccess;
import com.corpusai.auth.UserSubjectAccessRepository;
import com.corpusai.auth.UserSubjectId;
import com.corpusai.ingestion.StorageProperties;
import com.corpusai.metrics.LlmFeature;
import com.corpusai.metrics.LlmUsage;
import com.corpusai.metrics.LlmUsageRepository;
import com.corpusai.model.ModelProvider;
import com.corpusai.subject.Subject;
import com.corpusai.subject.SubjectService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ChatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserSubjectAccessRepository accessRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private SubjectService subjectService;

    @Autowired
    private StorageProperties storageProperties;

    @Autowired
    private ChatSessionRepository chatSessionRepository;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Autowired
    private LlmUsageRepository llmUsageRepository;

    private final List<String> createdSubjectIds = new ArrayList<>();

    @AfterEach
    void cleanupStorageDirs() throws IOException {
        for (String subjectId : createdSubjectIds) {
            Path dir = Path.of(storageProperties.root()).resolve(subjectId);
            if (Files.exists(dir)) {
                try (var stream = Files.walk(dir)) {
                    stream.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
                }
            }
        }
        createdSubjectIds.clear();
    }

    @Test
    void createSessionSucceedsForUserWithAccess() throws Exception {
        Subject subject = createTestSubject();
        User user = registerUser();
        grantAccess(user, subject.getId());
        String token = login(user.getEmail(), "password123");

        mockMvc.perform(post("/api/chats")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("""
                                {"subjectId":"%s","lang":"sr","provider":"OPENAI"}
                                """.formatted(subject.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.title").doesNotExist())
                .andExpect(jsonPath("$.subjectId").value(subject.getId()))
                .andExpect(jsonPath("$.lang").value("sr"))
                .andExpect(jsonPath("$.provider").value("OPENAI"));
    }

    @Test
    void createSessionFailsForUserWithoutAccess() throws Exception {
        Subject subject = createTestSubject();
        User user = registerUser();
        String token = login(user.getEmail(), "password123");

        mockMvc.perform(post("/api/chats")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("""
                                {"subjectId":"%s","lang":"sr","provider":"OPENAI"}
                                """.formatted(subject.getId())))
                .andExpect(status().isForbidden());
    }

    @Test
    void createSessionFailsForUnknownSubject() throws Exception {
        User user = registerUser();
        String token = login(user.getEmail(), "password123");

        mockMvc.perform(post("/api/chats")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("""
                                {"subjectId":"no-such-subject","lang":"sr","provider":"OPENAI"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }

    @Test
    void createSessionRejectsInvalidLang() throws Exception {
        Subject subject = createTestSubject();
        User user = registerUser();
        grantAccess(user, subject.getId());
        String token = login(user.getEmail(), "password123");

        mockMvc.perform(post("/api/chats")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("""
                                {"subjectId":"%s","lang":"fr","provider":"OPENAI"}
                                """.formatted(subject.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    void createSessionRejectsUnknownProvider() throws Exception {
        Subject subject = createTestSubject();
        User user = registerUser();
        grantAccess(user, subject.getId());
        String token = login(user.getEmail(), "password123");

        // An unmapped enum value fails Jackson deserialization before validation runs, so
        // this lands on the malformed-body handler rather than VALIDATION_ERROR.
        mockMvc.perform(post("/api/chats")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("""
                                {"subjectId":"%s","lang":"sr","provider":"GEMINI"}
                                """.formatted(subject.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"));
    }

    @Test
    void createSessionRejectsMissingFields() throws Exception {
        User user = registerUser();
        String token = login(user.getEmail(), "password123");

        mockMvc.perform(post("/api/chats")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    void listSessionsReturnsOwnSessionsNewestFirst() throws Exception {
        Subject subject = createTestSubject();
        User user = registerUser();
        grantAccess(user, subject.getId());
        String token = login(user.getEmail(), "password123");

        ChatSession older = createFixtureSession(user, subject.getId());
        ChatSession newer = createFixtureSession(user, subject.getId());
        newer.touch();
        chatSessionRepository.save(newer);

        mockMvc.perform(get("/api/chats").param("subjectId", subject.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(newer.getId().toString()))
                .andExpect(jsonPath("$[1].id").value(older.getId().toString()));
    }

    @Test
    void listSessionsOnlyReturnsCallersOwnSessions() throws Exception {
        Subject subject = createTestSubject();
        User owner = registerUser();
        User other = registerUser();
        grantAccess(owner, subject.getId());
        grantAccess(other, subject.getId());
        createFixtureSession(owner, subject.getId());
        String otherToken = login(other.getEmail(), "password123");

        mockMvc.perform(get("/api/chats").param("subjectId", subject.getId())
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void listSessionsFailsForUnknownSubject() throws Exception {
        User user = registerUser();
        String token = login(user.getEmail(), "password123");

        mockMvc.perform(get("/api/chats").param("subjectId", "no-such-subject")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }

    @Test
    void getMessagesReturnsFullTranscriptForOwner() throws Exception {
        Subject subject = createTestSubject();
        User user = registerUser();
        grantAccess(user, subject.getId());
        String token = login(user.getEmail(), "password123");

        ChatSession session = createFixtureSession(user, subject.getId());
        chatMessageRepository.save(new ChatMessage(session.getId(), MessageRole.USER, "hello"));
        chatMessageRepository.save(new ChatMessage(session.getId(), MessageRole.ASSISTANT, "hi there"));

        mockMvc.perform(get("/api/chats/{sessionId}/messages", session.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].role").value("USER"))
                .andExpect(jsonPath("$[0].content").value("hello"))
                .andExpect(jsonPath("$[1].role").value("ASSISTANT"))
                .andExpect(jsonPath("$[1].content").value("hi there"));
    }

    // The transcript is the only source of per-message stats after a reload - the done event is gone
    // by then - so the join from message to usage row is what makes the frontend's stats line survive.
    @Test
    void getMessagesReturnsUsageStatsOnTheAssistantMessageAndNullsOnTheUserMessage() throws Exception {
        Subject subject = createTestSubject();
        User user = registerUser();
        grantAccess(user, subject.getId());
        String token = login(user.getEmail(), "password123");

        ChatSession session = createFixtureSession(user, subject.getId());
        chatMessageRepository.save(new ChatMessage(session.getId(), MessageRole.USER, "hello"));
        ChatMessage reply = chatMessageRepository.save(
                new ChatMessage(session.getId(), MessageRole.ASSISTANT, "hi there"));
        llmUsageRepository.save(new LlmUsage(LlmFeature.CHAT, ModelProvider.ANTHROPIC, "claude-haiku-4-5",
                312, 88, 400, 2400L, user.getId(), subject.getId(), session.getId(), reply.getId(), Instant.now()));

        mockMvc.perform(get("/api/chats/{sessionId}/messages", session.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].role").value("USER"))
                .andExpect(jsonPath("$[0].inputTokens").doesNotExist())
                .andExpect(jsonPath("$[0].outputTokens").doesNotExist())
                .andExpect(jsonPath("$[0].latencyMs").doesNotExist())
                .andExpect(jsonPath("$[0].model").doesNotExist())
                .andExpect(jsonPath("$[1].role").value("ASSISTANT"))
                .andExpect(jsonPath("$[1].inputTokens").value(312))
                .andExpect(jsonPath("$[1].outputTokens").value(88))
                .andExpect(jsonPath("$[1].latencyMs").value(2400))
                .andExpect(jsonPath("$[1].model").value("claude-haiku-4-5"));
    }

    // Replies stored before usage rows carried a message id must still render, just without stats.
    @Test
    void getMessagesReturnsNullStatsForAnAssistantMessageWithNoUsageRow() throws Exception {
        Subject subject = createTestSubject();
        User user = registerUser();
        grantAccess(user, subject.getId());
        String token = login(user.getEmail(), "password123");

        ChatSession session = createFixtureSession(user, subject.getId());
        chatMessageRepository.save(new ChatMessage(session.getId(), MessageRole.ASSISTANT, "older reply"));

        mockMvc.perform(get("/api/chats/{sessionId}/messages", session.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].content").value("older reply"))
                .andExpect(jsonPath("$[0].inputTokens").doesNotExist())
                .andExpect(jsonPath("$[0].model").doesNotExist());
    }

    // A provider that reports no token counts still produces a row, and latency is measured by us
    // rather than by the provider - so it must survive even when both token counts are null.
    @Test
    void getMessagesReturnsLatencyAndModelEvenWhenTheProviderReportedNoTokens() throws Exception {
        Subject subject = createTestSubject();
        User user = registerUser();
        grantAccess(user, subject.getId());
        String token = login(user.getEmail(), "password123");

        ChatSession session = createFixtureSession(user, subject.getId());
        ChatMessage reply = chatMessageRepository.save(
                new ChatMessage(session.getId(), MessageRole.ASSISTANT, "no usage reported"));
        llmUsageRepository.save(new LlmUsage(LlmFeature.CHAT, ModelProvider.OPENAI, "gpt-5.4-mini",
                null, null, null, 1500L, user.getId(), subject.getId(), session.getId(), reply.getId(), Instant.now()));

        mockMvc.perform(get("/api/chats/{sessionId}/messages", session.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].inputTokens").doesNotExist())
                .andExpect(jsonPath("$[0].outputTokens").doesNotExist())
                .andExpect(jsonPath("$[0].latencyMs").value(1500))
                .andExpect(jsonPath("$[0].model").value("gpt-5.4-mini"));
    }

    // Usage rows from other sessions, and rows with no message id at all (flashcards, quizzes, query
    // compression), must never leak onto a transcript message.
    @Test
    void getMessagesIgnoresUsageRowsFromOtherSessionsAndRowsWithoutAMessageId() throws Exception {
        Subject subject = createTestSubject();
        User user = registerUser();
        grantAccess(user, subject.getId());
        String token = login(user.getEmail(), "password123");

        ChatSession session = createFixtureSession(user, subject.getId());
        ChatSession otherSession = createFixtureSession(user, subject.getId());
        ChatMessage reply = chatMessageRepository.save(
                new ChatMessage(session.getId(), MessageRole.ASSISTANT, "reply"));

        // Right message id, wrong session - the lookup is scoped by session, so this must not match.
        llmUsageRepository.save(new LlmUsage(LlmFeature.CHAT, ModelProvider.OPENAI, "wrong-session-model",
                1, 1, 2, 11L, user.getId(), subject.getId(), otherSession.getId(), reply.getId(), Instant.now()));
        // Right session, no message id - a flashcard-style row that happens to carry a session id.
        llmUsageRepository.save(new LlmUsage(LlmFeature.FLASHCARDS, ModelProvider.OPENAI, "no-message-model",
                2, 2, 4, 22L, user.getId(), subject.getId(), session.getId(), null, Instant.now()));

        mockMvc.perform(get("/api/chats/{sessionId}/messages", session.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].model").doesNotExist())
                .andExpect(jsonPath("$[0].inputTokens").doesNotExist());
    }

    @Test
    void getMessagesFailsForNonOwner() throws Exception {
        Subject subject = createTestSubject();
        User owner = registerUser();
        User other = registerUser();
        grantAccess(owner, subject.getId());
        String otherToken = login(other.getEmail(), "password123");

        ChatSession session = createFixtureSession(owner, subject.getId());

        mockMvc.perform(get("/api/chats/{sessionId}/messages", session.getId())
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void getMessagesFailsForUnknownSession() throws Exception {
        User user = registerUser();
        String token = login(user.getEmail(), "password123");

        mockMvc.perform(get("/api/chats/{sessionId}/messages", UUID.randomUUID())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteSessionRemovesSessionAndCascadesMessages() throws Exception {
        Subject subject = createTestSubject();
        User user = registerUser();
        grantAccess(user, subject.getId());
        String token = login(user.getEmail(), "password123");

        ChatSession session = createFixtureSession(user, subject.getId());
        chatMessageRepository.save(new ChatMessage(session.getId(), MessageRole.USER, "hello"));

        mockMvc.perform(delete("/api/chats/{sessionId}", session.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        assertThat(chatSessionRepository.findById(session.getId())).isEmpty();
        assertThat(chatMessageRepository.findAllBySessionIdOrderByCreatedAtAsc(session.getId())).isEmpty();
    }

    @Test
    void deleteSessionFailsForNonOwner() throws Exception {
        Subject subject = createTestSubject();
        User owner = registerUser();
        User other = registerUser();
        grantAccess(owner, subject.getId());
        String otherToken = login(other.getEmail(), "password123");

        ChatSession session = createFixtureSession(owner, subject.getId());

        mockMvc.perform(delete("/api/chats/{sessionId}", session.getId())
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden());

        assertThat(chatSessionRepository.findById(session.getId())).isPresent();
    }

    @Test
    void deleteSessionFailsForUnknownSession() throws Exception {
        User user = registerUser();
        String token = login(user.getEmail(), "password123");

        mockMvc.perform(delete("/api/chats/{sessionId}", UUID.randomUUID())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    private ChatSession createFixtureSession(User user, String subjectId) {
        return chatSessionRepository.save(new ChatSession(user.getId(), subjectId, "sr", ModelProvider.OPENAI));
    }

    private Subject createTestSubject() {
        Subject subject = subjectService.create("MockMvc Chat Subject " + UUID.randomUUID(), "SR Name", null);
        createdSubjectIds.add(subject.getId());
        return subject;
    }

    private User registerUser() {
        String email = "test-" + UUID.randomUUID() + "@example.com";
        return userRepository.save(new User(email, passwordEncoder.encode("password123"), "Test User", Role.USER));
    }

    private void grantAccess(User user, String subjectId) {
        accessRepository.save(new UserSubjectAccess(new UserSubjectId(user.getId(), subjectId), user.getId()));
    }

    private String login(String email, String password) throws Exception {
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
