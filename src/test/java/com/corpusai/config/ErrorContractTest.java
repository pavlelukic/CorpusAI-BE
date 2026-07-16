package com.corpusai.config;

import com.corpusai.auth.Role;
import com.corpusai.auth.User;
import com.corpusai.auth.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The executable form of the API error contract: every failure path answers with the
 * {error, message} shape and a status. Spring's own 4xx mapping 
 * was being shadowed by the Exception backstop in GlobalExceptionHandler,
 * which silently turned client errors into 500s.
 * Runs against the real dev Postgres (same convention as SecurityIntegrationTest); no LLM
 * keys needed, since every request here fails before reaching a model call.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ErrorContractTest {

    private static final String SEEDED_SUBJECT = "softverski-proces";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    // --- 400: malformed path variables ---

    @Test
    void nonUuidPathVariableReturnsBadRequestNotServerError() throws Exception {
        String token = registerAndLogin();

        mockMvc.perform(get("/api/quizzes/{id}", "not-a-uuid").header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("Invalid value for 'quizId': not-a-uuid"));
    }

    @Test
    void nonUuidPathVariableIsRejectedOnEveryUuidEndpoint() throws Exception {
        String token = registerAndLogin();

        mockMvc.perform(get("/api/flashcards/{id}", "not-a-uuid").header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"));

        mockMvc.perform(delete("/api/chats/{id}", "not-a-uuid").header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"));
    }

    @Test
    void nonUuidPathVariableOnAdminEndpointReturnsBadRequest() throws Exception {
        String adminToken = createAdminAndLogin();

        mockMvc.perform(post("/api/admin/users/{userId}/subjects/{subjectId}", "not-a-uuid", SEEDED_SUBJECT)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("Invalid value for 'userId': not-a-uuid"));
    }

    // --- 400: missing required inputs ---

    @Test
    void missingRequiredQueryParameterReturnsBadRequest() throws Exception {
        String token = registerAndLogin();

        mockMvc.perform(get("/api/chats").header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("Required parameter 'subjectId' is missing"));
    }

    @Test
    void missingMultipartFilePartReturnsBadRequest() throws Exception {
        String adminToken = createAdminAndLogin();
        MockMultipartFile wrongPart = new MockMultipartFile(
                "notfile", "note.md", "text/markdown", "content".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/admin/subjects/{id}/documents", SEEDED_SUBJECT)
                        .file(wrongPart)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("Required file part 'file' is missing"));
    }

    @Test
    void malformedJsonBodyReturnsBadRequest() throws Exception {
        String token = registerAndLogin();

        mockMvc.perform(post("/api/chats")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("Request body is missing or malformed"));
    }

    @Test
    void violatedBeanValidationReturnsValidationError() throws Exception {
        String token = registerAndLogin();

        mockMvc.perform(post("/api/chats")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"subjectId":"%s","lang":"fr","provider":"OPENAI"}
                                """.formatted(SEEDED_SUBJECT)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    // --- 401 / 403 ---

    @Test
    void missingTokenReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/subjects"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void nonAdminOnAdminRouteReturnsForbidden() throws Exception {
        String token = registerAndLogin();

        mockMvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    // --- 404 ---

    @Test
    void unknownSubjectReturnsNotFound() throws Exception {
        String token = registerAndLogin();

        mockMvc.perform(get("/api/chats").param("subjectId", "no-such-subject")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Unknown subject: no-such-subject"));
    }

    @Test
    void unknownRouteReturnsNotFound() throws Exception {
        String token = registerAndLogin();

        mockMvc.perform(get("/api/nonexistent").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("The requested resource was not found"));
    }

    // --- 405 / 409 / 415 ---

    @Test
    void unsupportedMethodReturnsMethodNotAllowed() throws Exception {
        String token = registerAndLogin();

        mockMvc.perform(delete("/api/subjects").header("Authorization", "Bearer " + token))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.error").value("METHOD_NOT_ALLOWED"))
                .andExpect(jsonPath("$.message").value("Method DELETE is not supported for this endpoint"));
    }

    @Test
    void duplicateRegistrationReturnsConflict() throws Exception {
        String email = uniqueEmail();
        register(email);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(email)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("CONFLICT"))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void wrongContentTypeReturnsUnsupportedMediaType() throws Exception {
        String token = registerAndLogin();

        mockMvc.perform(post("/api/chats")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("x"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.error").value("UNSUPPORTED_MEDIA_TYPE"))
                .andExpect(jsonPath("$.message").value("Content-Type must be application/json"));
    }

    private String uniqueEmail() {
        return "test-" + UUID.randomUUID() + "@example.com";
    }

    private String registerBody(String email) {
        return """
                {"email":"%s","password":"password123","displayName":"Test User"}
                """.formatted(email);
    }

    private void register(String email) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(email)))
                .andExpect(status().isOk());
    }

    private String registerAndLogin() throws Exception {
        String email = uniqueEmail();
        register(email);
        return login(email, "password123");
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

    private String createAdminAndLogin() throws Exception {
        String email = uniqueEmail();
        String password = "adminPass123";
        userRepository.save(new User(email, passwordEncoder.encode(password), "Test Admin", Role.ADMIN));
        return login(email, password);
    }
}