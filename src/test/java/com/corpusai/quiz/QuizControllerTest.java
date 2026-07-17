
package com.corpusai.quiz;

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
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Real dev Postgres + per-test rollback, mirroring FlashcardControllerTest. LLM-free: the
// generation happy-path calls a real model and is verified manually (both providers); here we
// seed quizzes with known correctIndexes via the repositories, so grading, the one-submit rule,
// the completion gate on GET, validation, ownership and cascade are all tested without a model.
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class QuizControllerTest {

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
    private QuizRepository quizRepository;

    @Autowired
    private QuizQuestionRepository quizQuestionRepository;

    @Autowired
    private UserSubjectAccessRepository accessRepository;

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

        mockMvc.perform(post("/api/quizzes/{subjectId}/generate", "any-subject")
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

        mockMvc.perform(post("/api/quizzes/{subjectId}/generate", "any-subject")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topic\":\"" + longTopic + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    void generateRejectsInvalidLang() throws Exception {
        String token = login(registerUser().getEmail());

        mockMvc.perform(post("/api/quizzes/{subjectId}/generate", "any-subject")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lang\":\"fr\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    void generateRejectsUnknownProvider() throws Exception {
        String token = login(registerUser().getEmail());

        mockMvc.perform(post("/api/quizzes/{subjectId}/generate", "any-subject")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provider\":\"GEMINI\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void generateFailsForUserWithoutAccess() throws Exception {
        Subject subject = createTestSubject();
        String token = login(registerUser().getEmail());

        mockMvc.perform(post("/api/quizzes/{subjectId}/generate", subject.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    @Test
    void generateFailsForUnknownSubject() throws Exception {
        String token = login(registerUser().getEmail());

        mockMvc.perform(post("/api/quizzes/{subjectId}/generate", "no-such-subject")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }

    // A subject the user can reach but that has no ingested documents: retrieval finds nothing, so
    // generation must stop with 409 before spending an LLM call, rather than 500ing on an empty
    // model response. Reachable in an LLM-free test precisely because it fails before the model.
    @Test
    void generateReturnsConflictWhenSubjectHasNoContent() throws Exception {
        Subject subject = createTestSubject();
        User user = registerUser();
        grantAccess(user, subject.getId());
        String token = login(user.getEmail());

        mockMvc.perform(post("/api/quizzes/{subjectId}/generate", subject.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("CONFLICT"));
    }

    // --- submit: server-side grading ---

    @Test
    void submitGradesMixedAnswersServerSide() throws Exception {
        Subject subject = createTestSubject();
        User user = registerUser();
        String token = login(user.getEmail());

        Quiz quiz = createFixtureQuiz(user, subject.getId());
        List<QuizQuestion> questions = seedFourQuestions(quiz);

        // correct indexes are 0,1,2,3: answer correct / wrong / correct / skipped -> 2 of 4
        mockMvc.perform(post("/api/quizzes/{quizId}/submit", quiz.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"answers":[
                                  {"questionId":"%s","selectedIndex":0},
                                  {"questionId":"%s","selectedIndex":2},
                                  {"questionId":"%s","selectedIndex":2}
                                ]}
                                """.formatted(questions.get(0).getId(), questions.get(1).getId(), questions.get(2).getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.score").value(2))
                .andExpect(jsonPath("$.total").value(4))
                .andExpect(jsonPath("$.results.length()").value(4))
                .andExpect(jsonPath("$.results[0].questionId").value(questions.get(0).getId().toString()))
                .andExpect(jsonPath("$.results[0].correct").value(true))
                .andExpect(jsonPath("$.results[1].correct").value(false))
                .andExpect(jsonPath("$.results[1].correctIndex").value(1))
                .andExpect(jsonPath("$.results[1].explanation").value("expl1"))
                .andExpect(jsonPath("$.results[2].correct").value(true))
                .andExpect(jsonPath("$.results[3].questionId").value(questions.get(3).getId().toString()))
                .andExpect(jsonPath("$.results[3].correct").value(false));

        Quiz completed = quizRepository.findById(quiz.getId()).orElseThrow();
        assertThat(completed.isCompleted()).isTrue();
        assertThat(completed.getScore()).isEqualTo(2);

        List<QuizQuestion> graded = quizQuestionRepository.findAllByQuizIdOrderByPositionAsc(quiz.getId());
        assertThat(graded.get(0).getSelectedIndex()).isEqualTo(0);
        assertThat(graded.get(1).getSelectedIndex()).isEqualTo(2);
        assertThat(graded.get(2).getSelectedIndex()).isEqualTo(2);
        assertThat(graded.get(3).getSelectedIndex()).isNull(); // skipped, not answered wrong
    }

    @Test
    void submitAllCorrectScoresFull() throws Exception {
        Subject subject = createTestSubject();
        User user = registerUser();
        String token = login(user.getEmail());

        Quiz quiz = createFixtureQuiz(user, subject.getId());
        List<QuizQuestion> questions = seedFourQuestions(quiz);

        mockMvc.perform(post("/api/quizzes/{quizId}/submit", quiz.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"answers":[
                                  {"questionId":"%s","selectedIndex":0},
                                  {"questionId":"%s","selectedIndex":1},
                                  {"questionId":"%s","selectedIndex":2},
                                  {"questionId":"%s","selectedIndex":3}
                                ]}
                                """.formatted(questions.get(0).getId(), questions.get(1).getId(),
                                questions.get(2).getId(), questions.get(3).getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.score").value(4))
                .andExpect(jsonPath("$.total").value(4));
    }

    @Test
    void resubmitReturns409() throws Exception {
        Subject subject = createTestSubject();
        User user = registerUser();
        String token = login(user.getEmail());

        Quiz quiz = createFixtureQuiz(user, subject.getId());
        List<QuizQuestion> questions = seedFourQuestions(quiz);
        String body = """
                {"answers":[{"questionId":"%s","selectedIndex":0}]}
                """.formatted(questions.get(0).getId());

        mockMvc.perform(post("/api/quizzes/{quizId}/submit", quiz.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/quizzes/{quizId}/submit", quiz.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("CONFLICT"));
    }

    @Test
    void submitRejectsForeignQuestionId() throws Exception {
        Subject subject = createTestSubject();
        User user = registerUser();
        String token = login(user.getEmail());

        Quiz quiz = createFixtureQuiz(user, subject.getId());
        seedFourQuestions(quiz);
        Quiz otherQuiz = createFixtureQuiz(user, subject.getId());
        List<QuizQuestion> otherQuestions = seedFourQuestions(otherQuiz);

        mockMvc.perform(post("/api/quizzes/{quizId}/submit", quiz.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"answers":[{"questionId":"%s","selectedIndex":0}]}
                                """.formatted(otherQuestions.get(0).getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"));

        assertThat(quizRepository.findById(quiz.getId()).orElseThrow().isCompleted()).isFalse();
    }

    @Test
    void submitRejectsDuplicateAnswers() throws Exception {
        Subject subject = createTestSubject();
        User user = registerUser();
        String token = login(user.getEmail());

        Quiz quiz = createFixtureQuiz(user, subject.getId());
        List<QuizQuestion> questions = seedFourQuestions(quiz);

        mockMvc.perform(post("/api/quizzes/{quizId}/submit", quiz.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"answers":[
                                  {"questionId":"%s","selectedIndex":0},
                                  {"questionId":"%s","selectedIndex":1}
                                ]}
                                """.formatted(questions.get(0).getId(), questions.get(0).getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"));
    }

    @Test
    void submitRejectsOutOfRangeSelectedIndex() throws Exception {
        Subject subject = createTestSubject();
        User user = registerUser();
        String token = login(user.getEmail());

        Quiz quiz = createFixtureQuiz(user, subject.getId());
        List<QuizQuestion> questions = seedFourQuestions(quiz);

        mockMvc.perform(post("/api/quizzes/{quizId}/submit", quiz.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"answers":[{"questionId":"%s","selectedIndex":7}]}
                                """.formatted(questions.get(0).getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    void submitRejectsEmptyAnswers() throws Exception {
        Subject subject = createTestSubject();
        User user = registerUser();
        String token = login(user.getEmail());

        Quiz quiz = createFixtureQuiz(user, subject.getId());
        seedFourQuestions(quiz);

        mockMvc.perform(post("/api/quizzes/{quizId}/submit", quiz.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answers\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    void submitFailsForNonOwner() throws Exception {
        Subject subject = createTestSubject();
        User owner = registerUser();
        User other = registerUser();
        String otherToken = login(other.getEmail());

        Quiz quiz = createFixtureQuiz(owner, subject.getId());
        List<QuizQuestion> questions = seedFourQuestions(quiz);

        mockMvc.perform(post("/api/quizzes/{quizId}/submit", quiz.getId())
                        .header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"answers":[{"questionId":"%s","selectedIndex":0}]}
                                """.formatted(questions.get(0).getId())))
                .andExpect(status().isForbidden());
    }

    @Test
    void submitFailsForUnknownQuiz() throws Exception {
        String token = login(registerUser().getEmail());

        mockMvc.perform(post("/api/quizzes/{quizId}/submit", UUID.randomUUID())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"answers":[{"questionId":"%s","selectedIndex":0}]}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isNotFound());
    }

    // --- get one quiz: the completion gate ---

    @Test
    void getQuizBeforeCompletionOmitsGradingFields() throws Exception {
        Subject subject = createTestSubject();
        User user = registerUser();
        String token = login(user.getEmail());

        Quiz quiz = createFixtureQuiz(user, subject.getId());
        seedFourQuestions(quiz);

        mockMvc.perform(get("/api/quizzes/{quizId}", quiz.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quizId").value(quiz.getId().toString()))
                .andExpect(jsonPath("$.score").value(nullValue()))
                .andExpect(jsonPath("$.completedAt").value(nullValue()))
                .andExpect(jsonPath("$.questions.length()").value(4))
                .andExpect(jsonPath("$.questions[0].options.length()").value(4))
                .andExpect(jsonPath("$.questions[0].correctIndex").doesNotExist())
                .andExpect(jsonPath("$.questions[0].explanation").doesNotExist())
                .andExpect(jsonPath("$.questions[0].selectedIndex").doesNotExist())
                .andExpect(jsonPath("$.questions[0].correct").doesNotExist());
    }

    @Test
    void getQuizAfterCompletionIncludesGradingFields() throws Exception {
        Subject subject = createTestSubject();
        User user = registerUser();
        String token = login(user.getEmail());

        Quiz quiz = createFixtureQuiz(user, subject.getId());
        List<QuizQuestion> questions = seedFourQuestions(quiz);

        // wrong answer on q1, q3 skipped
        mockMvc.perform(post("/api/quizzes/{quizId}/submit", quiz.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"answers":[
                                  {"questionId":"%s","selectedIndex":0},
                                  {"questionId":"%s","selectedIndex":2},
                                  {"questionId":"%s","selectedIndex":2}
                                ]}
                                """.formatted(questions.get(0).getId(), questions.get(1).getId(), questions.get(2).getId())))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/quizzes/{quizId}", quiz.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.score").value(2))
                .andExpect(jsonPath("$.questions[1].selectedIndex").value(2))
                .andExpect(jsonPath("$.questions[1].correct").value(false))
                .andExpect(jsonPath("$.questions[1].correctIndex").value(1))
                .andExpect(jsonPath("$.questions[1].explanation").value("expl1"))
                .andExpect(jsonPath("$.questions[3].selectedIndex").doesNotExist()) // skipped stays null
                .andExpect(jsonPath("$.questions[3].correct").value(false))
                .andExpect(jsonPath("$.questions[3].correctIndex").value(3));
    }

    @Test
    void getQuizFailsForNonOwner() throws Exception {
        Subject subject = createTestSubject();
        User owner = registerUser();
        User other = registerUser();
        String otherToken = login(other.getEmail());

        Quiz quiz = createFixtureQuiz(owner, subject.getId());

        mockMvc.perform(get("/api/quizzes/{quizId}", quiz.getId())
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void getQuizFailsForUnknownQuiz() throws Exception {
        String token = login(registerUser().getEmail());

        mockMvc.perform(get("/api/quizzes/{quizId}", UUID.randomUUID())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    // --- list ---

    @Test
    void listQuizzesReturnsOwnNewestFirst() throws Exception {
        Subject subject = createTestSubject();
        User user = registerUser();
        String token = login(user.getEmail());

        Quiz older = createFixtureQuiz(user, subject.getId());
        Thread.sleep(5); // guarantee distinct createdAt (no touch()/updatedAt on a quiz)
        Quiz newer = createFixtureQuiz(user, subject.getId());

        mockMvc.perform(get("/api/quizzes").param("subjectId", subject.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].quizId").value(newer.getId().toString()))
                .andExpect(jsonPath("$[0].questionCount").value(4))
                .andExpect(jsonPath("$[0].score").value(nullValue()))
                .andExpect(jsonPath("$[1].quizId").value(older.getId().toString()));
    }

    @Test
    void listQuizzesOnlyReturnsCallersOwnQuizzes() throws Exception {
        Subject subject = createTestSubject();
        User owner = registerUser();
        User other = registerUser();
        createFixtureQuiz(owner, subject.getId());
        String otherToken = login(other.getEmail());

        mockMvc.perform(get("/api/quizzes").param("subjectId", subject.getId())
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void listQuizzesFailsForUnknownSubject() throws Exception {
        String token = login(registerUser().getEmail());

        mockMvc.perform(get("/api/quizzes").param("subjectId", "no-such-subject")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }

    // --- delete ---

    @Test
    void deleteQuizRemovesQuizAndCascadesQuestions() throws Exception {
        Subject subject = createTestSubject();
        User user = registerUser();
        String token = login(user.getEmail());

        Quiz quiz = createFixtureQuiz(user, subject.getId());
        seedFourQuestions(quiz);

        mockMvc.perform(delete("/api/quizzes/{quizId}", quiz.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        assertThat(quizRepository.findById(quiz.getId())).isEmpty();
        assertThat(quizQuestionRepository.findAllByQuizIdOrderByPositionAsc(quiz.getId())).isEmpty();
    }

    @Test
    void deleteQuizFailsForNonOwner() throws Exception {
        Subject subject = createTestSubject();
        User owner = registerUser();
        User other = registerUser();
        String otherToken = login(other.getEmail());

        Quiz quiz = createFixtureQuiz(owner, subject.getId());

        mockMvc.perform(delete("/api/quizzes/{quizId}", quiz.getId())
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden());

        assertThat(quizRepository.findById(quiz.getId())).isPresent();
    }

    @Test
    void deleteQuizFailsForUnknownQuiz() throws Exception {
        String token = login(registerUser().getEmail());

        mockMvc.perform(delete("/api/quizzes/{quizId}", UUID.randomUUID())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    // --- fixtures ---

    private Quiz createFixtureQuiz(User user, String subjectId) {
        return quizRepository.save(new Quiz(user.getId(), subjectId, "Scrum", "sr", ModelProvider.OPENAI, 4));
    }

    private List<QuizQuestion> seedFourQuestions(Quiz quiz) {
        List<QuizQuestion> questions = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            questions.add(quizQuestionRepository.save(new QuizQuestion(
                    quiz.getId(), "Q" + i, List.of("opt0", "opt1", "opt2", "opt3"), i, "expl" + i, i)));
        }
        return questions;
    }

    private Subject createTestSubject() {
        Subject subject = subjectService.create("MockMvc Quiz Subject " + UUID.randomUUID(), "SR Name", null);
        createdSubjectIds.add(subject.getId());
        return subject;
    }

    private void grantAccess(User user, String subjectId) {
        accessRepository.save(new UserSubjectAccess(new UserSubjectId(user.getId(), subjectId), user.getId()));
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
