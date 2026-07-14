package com.corpusai.quiz;

import com.corpusai.model.ModelProvider;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "quizzes")
public class Quiz {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "subject_id", nullable = false)
    private String subjectId;

    @Column
    private String topic;

    @Column(nullable = false, length = 2)
    private String lang;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ModelProvider provider;

    @Column(name = "question_count", nullable = false)
    private int questionCount;

    @Column
    private Integer score;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Quiz() {
        // required by Hibernate
    }

    public Quiz(UUID userId, String subjectId, String topic, String lang, ModelProvider provider, int questionCount) {
        this.userId = userId;
        this.subjectId = subjectId;
        this.topic = topic;
        this.lang = lang;
        this.provider = provider;
        this.questionCount = questionCount;
        this.createdAt = Instant.now();
    }

    public void complete(int score) {
        this.score = score;
        this.completedAt = Instant.now();
    }

    public boolean isCompleted() {
        return completedAt != null;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getSubjectId() {
        return subjectId;
    }

    public String getTopic() {
        return topic;
    }

    public String getLang() {
        return lang;
    }

    public ModelProvider getProvider() {
        return provider;
    }

    public int getQuestionCount() {
        return questionCount;
    }

    public Integer getScore() {
        return score;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
