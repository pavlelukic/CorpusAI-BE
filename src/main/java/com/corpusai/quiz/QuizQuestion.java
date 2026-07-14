package com.corpusai.quiz;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "quiz_questions")
public class QuizQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "quiz_id", nullable = false)
    private UUID quizId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String question;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<String> options;

    @Column(name = "correct_index", nullable = false)
    private int correctIndex;

    @Column(columnDefinition = "TEXT")
    private String explanation;

    @Column(name = "selected_index")
    private Integer selectedIndex;

    @Column(nullable = false)
    private int position;

    protected QuizQuestion() {
        // required by Hibernate
    }

    public QuizQuestion(UUID quizId, String question, List<String> options, int correctIndex, String explanation, int position) {
        this.quizId = quizId;
        this.question = question;
        this.options = options;
        this.correctIndex = correctIndex;
        this.explanation = explanation;
        this.position = position;
    }

    public void recordAnswer(int selectedIndex) {
        this.selectedIndex = selectedIndex;
    }

    public UUID getId() {
        return id;
    }

    public UUID getQuizId() {
        return quizId;
    }

    public String getQuestion() {
        return question;
    }

    public List<String> getOptions() {
        return options;
    }

    public int getCorrectIndex() {
        return correctIndex;
    }

    public String getExplanation() {
        return explanation;
    }

    public Integer getSelectedIndex() {
        return selectedIndex;
    }

    public int getPosition() {
        return position;
    }
}
