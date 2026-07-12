package com.corpusai.chat;

import com.corpusai.auth.Role;
import com.corpusai.auth.User;
import com.corpusai.auth.UserRepository;
import com.corpusai.auth.UserSubjectAccess;
import com.corpusai.auth.UserSubjectAccessRepository;
import com.corpusai.auth.UserSubjectId;
import com.corpusai.ingestion.StorageProperties;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Covers only session creation (POST /api/chats), which needs no live LLM call. Sending a
// message goes through a real streaming model call and persists via ChatMemoryStoreImpl on
// a separate thread.
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
