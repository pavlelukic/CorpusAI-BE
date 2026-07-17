package com.corpusai.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Runs against the real dev Postgres (same convention as SecurityIntegrationTest); no LLM
// keys needed. SecurityIntegrationTest covers the grant -> visible -> revoke happy path;
// this file covers the response shape, the role guards on each verb, and the edge cases.
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminUserControllerTest {

    private static final String SEEDED_SUBJECT = "softverski-proces";

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

    @Test
    void listUsersReturnsUsersWithGrantedSubjectIds() throws Exception {
        String adminToken = createAdminAndLogin();
        String email = uniqueEmail();
        String userId = registerAndGetId(email);

        mockMvc.perform(post("/api/admin/users/{userId}/subjects/{subjectId}", userId, SEEDED_SUBJECT)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        // The users table is shared with every other test's fixtures, so assert against the
        // one row this test created rather than against the list as a whole.
        mockMvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '%s')].email".formatted(userId), hasItem(email)))
                .andExpect(jsonPath("$[?(@.id == '%s')].displayName".formatted(userId), hasItem("Test User")))
                .andExpect(jsonPath("$[?(@.id == '%s')].role".formatted(userId), hasItem("USER")))
                .andExpect(jsonPath("$[?(@.id == '%s')].subjectIds[0]".formatted(userId), hasItem(SEEDED_SUBJECT)));
    }

    @Test
    void nonAdminCannotGrantAccess() throws Exception {
        String email = uniqueEmail();
        String userId = registerAndGetId(email);
        String userToken = login(email);

        mockMvc.perform(post("/api/admin/users/{userId}/subjects/{subjectId}", userId, SEEDED_SUBJECT)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));

        assertThat(accessRepository.existsById(new UserSubjectId(UUID.fromString(userId), SEEDED_SUBJECT))).isFalse();
    }

    @Test
    void nonAdminCannotRevokeAccess() throws Exception {
        String email = uniqueEmail();
        String userId = registerAndGetId(email);
        String userToken = login(email);

        mockMvc.perform(delete("/api/admin/users/{userId}/subjects/{subjectId}", userId, SEEDED_SUBJECT)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    // The message assertions below distinguish our UserNotFoundException/SubjectNotFoundException
    // handlers from Spring's generic route-not-found, which also answers {error: NOT_FOUND}.

    @Test
    void grantingToUnknownUserReturnsNotFound() throws Exception {
        String adminToken = createAdminAndLogin();
        UUID unknownUserId = UUID.randomUUID();

        mockMvc.perform(post("/api/admin/users/{userId}/subjects/{subjectId}", unknownUserId, SEEDED_SUBJECT)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Unknown user: " + unknownUserId));
    }

    @Test
    void grantingUnknownSubjectReturnsNotFound() throws Exception {
        String adminToken = createAdminAndLogin();
        String userId = registerAndGetId(uniqueEmail());

        mockMvc.perform(post("/api/admin/users/{userId}/subjects/{subjectId}", userId, "no-such-subject")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Unknown subject: no-such-subject"));
    }

    @Test
    void grantingTwiceIsIdempotent() throws Exception {
        String adminToken = createAdminAndLogin();
        String userId = registerAndGetId(uniqueEmail());

        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/admin/users/{userId}/subjects/{subjectId}", userId, SEEDED_SUBJECT)
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isNoContent());
        }

        assertThat(accessRepository.findAllByIdUserId(UUID.fromString(userId))).hasSize(1);
    }

    @Test
    void revokingNonExistentGrantIsIdempotent() throws Exception {
        String adminToken = createAdminAndLogin();
        String userId = registerAndGetId(uniqueEmail());

        mockMvc.perform(delete("/api/admin/users/{userId}/subjects/{subjectId}", userId, SEEDED_SUBJECT)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        assertThat(accessRepository.findAllByIdUserId(UUID.fromString(userId))).isEmpty();
    }

    private String uniqueEmail() {
        return "test-" + UUID.randomUUID() + "@example.com";
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
        return loginWithPassword(email, "password123");
    }

    private String loginWithPassword(String email, String password) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("token").asText();
    }

    // Created directly via the repository (register always creates USER) so this test
    // doesn't depend on whatever ADMIN_EMAIL/ADMIN_PASSWORD happen to be set in .env.
    private String createAdminAndLogin() throws Exception {
        String email = uniqueEmail();
        String password = "adminPass123";
        userRepository.save(new User(email, passwordEncoder.encode(password), "Test Admin", Role.ADMIN));
        return loginWithPassword(email, password);
    }
}