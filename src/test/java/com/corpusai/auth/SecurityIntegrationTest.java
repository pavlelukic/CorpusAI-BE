package com.corpusai.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;

import static org.hamcrest.Matchers.empty;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Runs against the real dev Postgres (same as IngestionSmokeTest); no LLM keys needed
// since chat/flashcard requests here are rejected by the access check before any model call.
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtProperties jwtProperties;

    @Test
    void registerLoginAndMeFlowWorks() throws Exception {
        String email = uniqueEmail();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(email, "password123", "Test User")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.user.email").value(email))
                .andExpect(jsonPath("$.user.role").value("USER"));

        String token = login(email, "password123");

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email));
    }

    @Test
    void registeringTheSameEmailTwiceReturnsConflict() throws Exception {
        String email = uniqueEmail();
        register(email, "password123", "First");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(email, "password123", "Second")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("CONFLICT"));
    }

    @Test
    void registerWithInvalidPayloadReturnsValidationError() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("not-an-email", "short", "")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    void loginWithWrongPasswordReturnsUnauthorized() throws Exception {
        String email = uniqueEmail();
        register(email, "password123", "Test User");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(email, "wrongpassword")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
    }

    @Test
    void meWithoutTokenReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
    }

    @Test
    void subjectsWithoutTokenReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/subjects"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void subjectsListIsEmptyUntilAccessIsGranted() throws Exception {
        String email = uniqueEmail();
        register(email, "password123", "Test User");
        String token = login(email, "password123");

        mockMvc.perform(get("/api/subjects").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", empty()));
    }

    @Test
    void chatAndFlashcardsAreForbiddenWithoutSubjectAccess() throws Exception {
        String email = uniqueEmail();
        register(email, "password123", "Test User");
        String token = login(email, "password123");

        mockMvc.perform(post("/api/chats")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"subjectId":"softverski-proces","lang":"sr","provider":"OPENAI"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));

        mockMvc.perform(post("/api/flashcards/softverski-proces/generate")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    @Test
    void nonAdminCannotReachAdminEndpoints() throws Exception {
        String email = uniqueEmail();
        register(email, "password123", "Test User");
        String token = login(email, "password123");

        mockMvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanGrantAndRevokeSubjectAccess() throws Exception {
        String userEmail = uniqueEmail();
        String userId = registerAndGetId(userEmail, "password123", "Test User");
        String userToken = login(userEmail, "password123");
        String adminToken = createAdminAndLogin();

        mockMvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/admin/users/{userId}/subjects/{subjectId}", userId, "softverski-proces")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/subjects").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("softverski-proces"));

        mockMvc.perform(delete("/api/admin/users/{userId}/subjects/{subjectId}", userId, "softverski-proces")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/subjects").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", empty()));
    }

    @Test
    void actuatorHealthIsPublic() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    // --- token rejection matrix ---
    // JwtAuthenticationFilter silently leaves the context unauthenticated for any token it
    // can't verify, so every case below lands on JsonAuthenticationEntryPoint's 401 body.

    @Test
    void garbageTokenReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer not.a.jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
    }

    @Test
    void tokenWithTamperedSignatureReturnsUnauthorized() throws Exception {
        String email = uniqueEmail();
        register(email, "password123", "Test User");
        String token = login(email, "password123");

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + tamperSignature(token)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
    }

    @Test
    void expiredTokenReturnsUnauthorized() throws Exception {
        String expired = tokenWithExpiry(
                Keys.hmacShaKeyFor(jwtProperties.secret().getBytes(StandardCharsets.UTF_8)),
                Instant.now().minus(2, ChronoUnit.HOURS));

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + expired))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
    }

    @Test
    void tokenSignedWithWrongSecretReturnsUnauthorized() throws Exception {
        SecretKey foreignKey = Keys.hmacShaKeyFor(
                "a-completely-different-secret-that-is-long-enough-for-hmac-sha256".getBytes(StandardCharsets.UTF_8));
        String foreignToken = tokenWithExpiry(foreignKey, Instant.now().plus(1, ChronoUnit.HOURS));

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + foreignToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
    }

    @Test
    void tokenWithoutBearerPrefixReturnsUnauthorized() throws Exception {
        String email = uniqueEmail();
        register(email, "password123", "Test User");
        String token = login(email, "password123");

        mockMvc.perform(get("/api/auth/me").header("Authorization", token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
    }

    // Flips the FIRST character of the signature segment, leaving header/payload intact.
    // Not the last character: an HMAC-SHA256 signature is 32 bytes, which base64url-encodes to
    // 43 chars where the final one carries just 4 significant bits and 2 bits of padding. A swap
    // that only moves those padding bits decodes to the same signature and still verifies, so
    // tampering there fails to reject roughly 1 run in 16. Every bit of the first char counts.
    private String tamperSignature(String token) {
        int signatureStart = token.lastIndexOf('.') + 1;
        char first = token.charAt(signatureStart);
        return token.substring(0, signatureStart)
                + (first == 'A' ? 'B' : 'A')
                + token.substring(signatureStart + 1);
    }

    private String tokenWithExpiry(SecretKey key, Instant expiry) {
        return Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .claim("role", Role.USER.name())
                .issuedAt(Date.from(expiry.minus(1, ChronoUnit.HOURS)))
                .expiration(Date.from(expiry))
                .signWith(key)
                .compact();
    }

    private String uniqueEmail() {
        return "test-" + UUID.randomUUID() + "@example.com";
    }

    private String registerBody(String email, String password, String displayName) {
        return """
                {"email":"%s","password":"%s","displayName":"%s"}
                """.formatted(email, password, displayName);
    }

    private String loginBody(String email, String password) {
        return """
                {"email":"%s","password":"%s"}
                """.formatted(email, password);
    }

    private void register(String email, String password, String displayName) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(email, password, displayName)))
                .andExpect(status().isOk());
    }

    private String registerAndGetId(String email, String password, String displayName) throws Exception {
        String body = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(email, password, displayName)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("user").get("id").asText();
    }

    private String login(String email, String password) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(email, password)))
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
        return login(email, password);
    }
}
