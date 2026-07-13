package com.corpusai.flashcards;

import com.corpusai.quiz.Difficulty;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "flashcards")
public class Flashcard {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "set_id", nullable = false)
    private UUID setId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String question;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String answer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Difficulty difficulty;

    @Column(name = "source_hint", columnDefinition = "TEXT")
    private String sourceHint;

    @Column(nullable = false)
    private int position;

    protected Flashcard() {
        // required by Hibernate
    }

    public Flashcard(UUID setId, String question, String answer, Difficulty difficulty, String sourceHint, int position) {
        this.setId = setId;
        this.question = question;
        this.answer = answer;
        this.difficulty = difficulty;
        this.sourceHint = sourceHint;
        this.position = position;
    }

    public UUID getId() {
        return id;
    }

    public UUID getSetId() {
        return setId;
    }

    public String getQuestion() {
        return question;
    }

    public String getAnswer() {
        return answer;
    }

    public Difficulty getDifficulty() {
        return difficulty;
    }

    public String getSourceHint() {
        return sourceHint;
    }

    public int getPosition() {
        return position;
    }
}
