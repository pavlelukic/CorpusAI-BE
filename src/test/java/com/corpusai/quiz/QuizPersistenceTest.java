package com.corpusai.quiz;

import com.corpusai.auth.Role;
import com.corpusai.auth.User;
import com.corpusai.auth.UserRepository;
import com.corpusai.model.ModelProvider;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// Proves the JSONB mapping (first JSON column in the codebase): options must survive a real
// write-then-read against Postgres, not just Hibernate's boot-time schema validation.
@SpringBootTest
@Transactional
class QuizPersistenceTest {

    private static final String SUBJECT_ID = "softverski-proces";

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private QuizRepository quizRepository;

    @Autowired
    private QuizQuestionRepository quizQuestionRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void optionsListSurvivesJsonbRoundTrip() {
        User user = userRepository.save(
                new User("quiz-persistence@test.local", "irrelevant-hash", "Quiz Persistence", Role.USER));

        Quiz quiz = quizRepository.save(
                new Quiz(user.getId(), SUBJECT_ID, "jsonb round-trip", "sr", ModelProvider.ANTHROPIC, 2));

        List<String> first = List.of("Vodopadni model", "Agilni razvoj", "Spiralni model", "V-model");
        List<String> second = List.of("plain", "with \"quotes\"", "šđčćž unicode", "commas, and: colons");

        // saved out of position order on purpose — the reload below must come back sorted
        quizQuestionRepository.save(new QuizQuestion(quiz.getId(), "Drugo pitanje?", second, 3, null, 1));
        quizQuestionRepository.save(new QuizQuestion(quiz.getId(), "Prvo pitanje?", first, 0, "Zato.", 0));

        entityManager.flush();
        entityManager.clear();

        List<QuizQuestion> reloaded = quizQuestionRepository.findAllByQuizIdOrderByPositionAsc(quiz.getId());

        assertThat(reloaded).hasSize(2);
        assertThat(reloaded.get(0).getQuestion()).isEqualTo("Prvo pitanje?");
        assertThat(reloaded.get(0).getOptions()).containsExactlyElementsOf(first);
        assertThat(reloaded.get(0).getExplanation()).isEqualTo("Zato.");
        assertThat(reloaded.get(1).getOptions()).containsExactlyElementsOf(second);
        assertThat(reloaded.get(1).getExplanation()).isNull();

        Quiz reloadedQuiz = quizRepository.findById(quiz.getId()).orElseThrow();
        assertThat(reloadedQuiz.isCompleted()).isFalse();
        assertThat(reloadedQuiz.getScore()).isNull();
        assertThat(reloadedQuiz.getQuestionCount()).isEqualTo(2);
    }

    @Test
    void completeAndRecordAnswerPersist() {
        User user = userRepository.save(
                new User("quiz-mutators@test.local", "irrelevant-hash", "Quiz Mutators", Role.USER));

        Quiz quiz = quizRepository.save(
                new Quiz(user.getId(), SUBJECT_ID, null, "en", ModelProvider.OPENAI, 1));
        QuizQuestion question = quizQuestionRepository.save(
                new QuizQuestion(quiz.getId(), "Q?", List.of("a", "b", "c", "d"), 2, null, 0));

        question.recordAnswer(2);
        quiz.complete(1);

        entityManager.flush();
        entityManager.clear();

        QuizQuestion reloadedQuestion = quizQuestionRepository.findById(question.getId()).orElseThrow();
        assertThat(reloadedQuestion.getSelectedIndex()).isEqualTo(2);

        Quiz reloadedQuiz = quizRepository.findById(quiz.getId()).orElseThrow();
        assertThat(reloadedQuiz.isCompleted()).isTrue();
        assertThat(reloadedQuiz.getScore()).isEqualTo(1);
        assertThat(reloadedQuiz.getCompletedAt()).isNotNull();
    }
}
