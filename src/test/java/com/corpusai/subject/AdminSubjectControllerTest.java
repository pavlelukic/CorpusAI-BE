package com.corpusai.subject;

import com.corpusai.auth.Role;
import com.corpusai.auth.User;
import com.corpusai.auth.UserRepository;
import com.corpusai.ingestion.StorageProperties;
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

import static org.hamcrest.Matchers.empty;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Runs against the real dev Postgres (same convention as SecurityIntegrationTest); no LLM
// keys needed since nothing here reaches ingestion or chat. Subject creation writes a real
// directory under storage.root that @Transactional rollback can't undo (it's a filesystem
// side effect, not a DB one) so every created subject is tracked and cleaned up afterward.
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminSubjectControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

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
    void nonAdminCannotCreateSubject() throws Exception {
        String userToken = registerAndLogin(uniqueEmail());

        mockMvc.perform(post("/api/admin/subjects")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"displayName":"X","displayNameSr":"Y"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void createWithBlankDisplayNameReturnsValidationError() throws Exception {
        String adminToken = createAdminAndLogin();

        mockMvc.perform(post("/api/admin/subjects")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"displayName":"","displayNameSr":"Y"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    void adminCanCreateUpdateAndArchiveSubjectRevokingAccess() throws Exception {
        String adminToken = createAdminAndLogin();
        String displayName = "MockMvc Subject " + UUID.randomUUID();

        String createBody = mockMvc.perform(post("/api/admin/subjects")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"displayName":"%s","displayNameSr":"SR Name"}
                                """.formatted(displayName)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.archived").value(false))
                .andReturn().getResponse().getContentAsString();
        String subjectId = objectMapper.readTree(createBody).get("id").asText();
        createdSubjectIds.add(subjectId);

        mockMvc.perform(post("/api/admin/subjects")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"displayName":"%s","displayNameSr":"SR Name"}
                                """.formatted(displayName)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("CONFLICT"));

        mockMvc.perform(put("/api/admin/subjects/{id}", subjectId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"displayName":"Renamed","displayNameSr":"SR Renamed","systemPrompt":"Custom prompt"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Renamed"))
                .andExpect(jsonPath("$.systemPrompt").value("Custom prompt"));

        String userEmail = uniqueEmail();
        String userId = registerAndGetId(userEmail);
        String userToken = login(userEmail);

        mockMvc.perform(post("/api/admin/users/{userId}/subjects/{subjectId}", userId, subjectId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/subjects").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(subjectId));

        mockMvc.perform(delete("/api/admin/subjects/{id}", subjectId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/subjects").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", empty()));
    }

    private String uniqueEmail() {
        return "test-" + UUID.randomUUID() + "@example.com";
    }

    private String registerAndLogin(String email) throws Exception {
        registerAndGetId(email);
        return login(email);
    }

    private String registerAndGetId(String email) throws Exception {
        String body = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"password123","displayName":"Test User"}
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("user").get("id").asText();
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
        String email = uniqueEmail();
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
