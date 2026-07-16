package com.corpusai.flashcards;

import com.corpusai.auth.Role;
import com.corpusai.auth.User;
import com.corpusai.auth.UserRepository;
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

// Real dev Postgres + per-test rollback, mirroring ChatControllerTest. LLM-free: the generation
// happy-path calls a real model and is verified manually (both providers); here we seed sets/cards
// directly via the repositories and cover validation, history, ownership and cascade only.
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class FlashcardControllerTest {

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
    private StorageProperties storageProperties;

    @Autowired
    private FlashcardSetRepository flashcardSetRepository;

    @Autowired
    private FlashcardRepository flashcardRepository;

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

    // --- generate: validation & guards (never reach the model) ---

    @Test
    void generateRejectsCountAbove20() throws Exception {
        String token = login(registerUser().getEmail());

        mockMvc.perform(post("/api/flashcards/{subjectId}/generate", "any-subject")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"count\":21}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    void generateRejectsTooLongTopic() throws Exception {
        String token = login(registerUser().getEmail());
        String longTopic = "x".repeat(201);

        mockMvc.perform(post("/api/flashcards/{subjectId}/generate", "any-subject")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topic\":\"" + longTopic + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    void generateRejectsInvalidLang() throws Exception {
        String token = login(registerUser().getEmail());

        mockMvc.perform(post("/api/flashcards/{subjectId}/generate", "any-subject")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lang\":\"fr\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    void generateRejectsUnknownProvider() throws Exception {
        String token = login(registerUser().getEmail());

        mockMvc.perform(post("/api/flashcards/{subjectId}/generate", "any-subject")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provider\":\"GEMINI\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void generateFailsForUserWithoutAccess() throws Exception {
        Subject subject = createTestSubject();
        String token = login(registerUser().getEmail());

        mockMvc.perform(post("/api/flashcards/{subjectId}/generate", subject.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    @Test
    void generateFailsForUnknownSubject() throws Exception {
        String token = login(registerUser().getEmail());

        mockMvc.perform(post("/api/flashcards/{subjectId}/generate", "no-such-subject")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }

    // --- list ---

    @Test
    void listSetsReturnsOwnSetsNewestFirst() throws Exception {
        Subject subject = createTestSubject();
        User user = registerUser();
        String token = login(user.getEmail());

        FlashcardSet older = createFixtureSet(user, subject.getId());
        Thread.sleep(5); // guarantee distinct createdAt (no touch()/updatedAt on a set)
        FlashcardSet newer = createFixtureSet(user, subject.getId());

        mockMvc.perform(get("/api/flashcards").param("subjectId", subject.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].setId").value(newer.getId().toString()))
                .andExpect(jsonPath("$[1].setId").value(older.getId().toString()));
    }

    @Test
    void listSetsOnlyReturnsCallersOwnSets() throws Exception {
        Subject subject = createTestSubject();
        User owner = registerUser();
        User other = registerUser();
        createFixtureSet(owner, subject.getId());
        String otherToken = login(other.getEmail());

        mockMvc.perform(get("/api/flashcards").param("subjectId", subject.getId())
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void listSetsFailsForUnknownSubject() throws Exception {
        String token = login(registerUser().getEmail());

        mockMvc.perform(get("/api/flashcards").param("subjectId", "no-such-subject")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }

    // --- get one set ---

    @Test
    void getSetReturnsCardsInPositionOrderForOwner() throws Exception {
        Subject subject = createTestSubject();
        User user = registerUser();
        String token = login(user.getEmail());

        FlashcardSet set = createFixtureSet(user, subject.getId());
        flashcardRepository.save(new Flashcard(set.getId(), "Q0", "A0", Difficulty.EASY, "hint0", 0));
        flashcardRepository.save(new Flashcard(set.getId(), "Q1", "A1", Difficulty.MEDIUM, "hint1", 1));
        flashcardRepository.save(new Flashcard(set.getId(), "Q2", "A2", Difficulty.HARD, "hint2", 2));

        mockMvc.perform(get("/api/flashcards/{setId}", set.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.setId").value(set.getId().toString()))
                .andExpect(jsonPath("$.cards.length()").value(3))
                .andExpect(jsonPath("$.cards[0].question").value("Q0"))
                .andExpect(jsonPath("$.cards[0].difficulty").value("EASY"))
                .andExpect(jsonPath("$.cards[2].question").value("Q2"))
                .andExpect(jsonPath("$.cards[2].difficulty").value("HARD"));
    }

    @Test
    void getSetFailsForNonOwner() throws Exception {
        Subject subject = createTestSubject();
        User owner = registerUser();
        User other = registerUser();
        String otherToken = login(other.getEmail());

        FlashcardSet set = createFixtureSet(owner, subject.getId());

        mockMvc.perform(get("/api/flashcards/{setId}", set.getId())
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void getSetFailsForUnknownSet() throws Exception {
        String token = login(registerUser().getEmail());

        mockMvc.perform(get("/api/flashcards/{setId}", UUID.randomUUID())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    // --- delete ---

    @Test
    void deleteSetRemovesSetAndCascadesCards() throws Exception {
        Subject subject = createTestSubject();
        User user = registerUser();
        String token = login(user.getEmail());

        FlashcardSet set = createFixtureSet(user, subject.getId());
        flashcardRepository.save(new Flashcard(set.getId(), "Q0", "A0", Difficulty.EASY, "hint0", 0));

        mockMvc.perform(delete("/api/flashcards/{setId}", set.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        assertThat(flashcardSetRepository.findById(set.getId())).isEmpty();
        assertThat(flashcardRepository.findAllBySetIdOrderByPositionAsc(set.getId())).isEmpty();
    }

    @Test
    void deleteSetFailsForNonOwner() throws Exception {
        Subject subject = createTestSubject();
        User owner = registerUser();
        User other = registerUser();
        String otherToken = login(other.getEmail());

        FlashcardSet set = createFixtureSet(owner, subject.getId());

        mockMvc.perform(delete("/api/flashcards/{setId}", set.getId())
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden());

        assertThat(flashcardSetRepository.findById(set.getId())).isPresent();
    }

    @Test
    void deleteSetFailsForUnknownSet() throws Exception {
        String token = login(registerUser().getEmail());

        mockMvc.perform(delete("/api/flashcards/{setId}", UUID.randomUUID())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    // --- fixtures ---

    private FlashcardSet createFixtureSet(User user, String subjectId) {
        return flashcardSetRepository.save(new FlashcardSet(user.getId(), subjectId, "Scrum", "sr", ModelProvider.OPENAI));
    }

    private Subject createTestSubject() {
        Subject subject = subjectService.create("MockMvc Flashcard Subject " + UUID.randomUUID(), "SR Name", null);
        createdSubjectIds.add(subject.getId());
        return subject;
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
}
