package com.corpusai.chat;

import com.corpusai.auth.Role;
import com.corpusai.auth.User;
import com.corpusai.auth.UserRepository;
import com.corpusai.auth.UserSubjectAccess;
import com.corpusai.auth.UserSubjectAccessRepository;
import com.corpusai.auth.UserSubjectId;
import com.corpusai.ingestion.StorageProperties;
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
                .andExpect(status().isBadRequest());
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
                .andExpect(status().isBadRequest());
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
