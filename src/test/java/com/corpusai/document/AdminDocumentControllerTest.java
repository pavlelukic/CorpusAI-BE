package com.corpusai.document;

import com.corpusai.auth.Role;
import com.corpusai.auth.User;
import com.corpusai.auth.UserRepository;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Runs against the real dev Postgres, no LLM keys needed. Deliberately does NOT poll
// upload results to READY or assert embeddings: ingestAsync runs on a separate thread
// with its own transaction, and since this test's rows never actually commit (rolled
// back at test end), that thread can never see them via its own connection - the full
// upload-to-READY flow was already verified manually against a real boot. list/delete
// are tested against fixture rows inserted directly via the repository, sidestepping
// the async path entirely so no real ingestion or OpenAI calls happen during the suite.
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminDocumentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private SubjectService subjectService;

    @Autowired
    private DocumentRepository documentRepository;

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
    void nonAdminCannotUploadDocument() throws Exception {
        String subjectId = createTestSubject();
        String userToken = registerAndLogin();
        MockMultipartFile file = new MockMultipartFile("file", "note.md", "text/markdown", "content".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/admin/subjects/{subjectId}/documents", subjectId)
                        .file(file)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void uploadRejectsUnsupportedFileType() throws Exception {
        String subjectId = createTestSubject();
        String adminToken = createAdminAndLogin();
        MockMultipartFile file = new MockMultipartFile("file", "note.exe", "application/octet-stream", "content".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/admin/subjects/{subjectId}/documents", subjectId)
                        .file(file)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"));
    }

    @Test
    void uploadToUnknownSubjectReturnsNotFound() throws Exception {
        String adminToken = createAdminAndLogin();
        MockMultipartFile file = new MockMultipartFile("file", "note.md", "text/markdown", "content".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/admin/subjects/{subjectId}/documents", "no-such-subject")
                        .file(file)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }

    @Test
    void uploadAcceptsValidFileAndReturnsPending() throws Exception {
        String subjectId = createTestSubject();
        String adminToken = createAdminAndLogin();
        MockMultipartFile file = new MockMultipartFile("file", "note.md", "text/markdown", "content".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/admin/subjects/{subjectId}/documents", subjectId)
                        .file(file)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.fileName").value("note.md"))
                .andExpect(jsonPath("$.status").value("PENDING"));

        mockMvc.perform(get("/api/admin/subjects/{subjectId}/documents", subjectId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].fileName").value("note.md"));
    }

    @Test
    void nonAdminCannotListDocuments() throws Exception {
        String subjectId = createTestSubject();
        String userToken = registerAndLogin();

        mockMvc.perform(get("/api/admin/subjects/{subjectId}/documents", subjectId)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteUnknownDocumentReturnsNotFound() throws Exception {
        String adminToken = createAdminAndLogin();

        mockMvc.perform(delete("/api/admin/documents/{documentId}", UUID.randomUUID())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }

    @Test
    void deleteRemovesFixtureDocumentRow() throws Exception {
        String subjectId = createTestSubject();
        String adminToken = createAdminAndLogin();

        Document document = new Document(subjectId, "fixture.md", subjectId + "/fixture.md", null);
        document.markReady("fixture-hash");
        document = documentRepository.save(document);

        mockMvc.perform(delete("/api/admin/documents/{documentId}", document.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/admin/subjects/{subjectId}/documents", subjectId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    private String createTestSubject() {
        Subject subject = subjectService.create("MockMvc Doc Subject " + UUID.randomUUID(), "SR Name", null);
        createdSubjectIds.add(subject.getId());
        return subject.getId();
    }

    private String uniqueEmail() {
        return "test-" + UUID.randomUUID() + "@example.com";
    }

    private String registerAndLogin() throws Exception {
        String email = uniqueEmail();
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"password123","displayName":"Test User"}
                                """.formatted(email)))
                .andExpect(status().isOk());
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
