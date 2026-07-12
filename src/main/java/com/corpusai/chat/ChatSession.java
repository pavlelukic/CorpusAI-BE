package com.corpusai.chat;

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
@Table(name = "chat_sessions")
public class ChatSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "subject_id", nullable = false)
    private String subjectId;

    @Column
    private String title;

    @Column(nullable = false, length = 2)
    private String lang;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ModelProvider provider;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ChatSession() {
        // required by Hibernate
    }

    public ChatSession(UUID userId, String subjectId, String lang, ModelProvider provider) {
        this.userId = userId;
        this.subjectId = subjectId;
        this.lang = lang;
        this.provider = provider;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void assignTitle(String title) {
        this.title = title;
    }

    public void touch() {
        this.updatedAt = Instant.now();
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

    public String getTitle() {
        return title;
    }

    public String getLang() {
        return lang;
    }

    public ModelProvider getProvider() {
        return provider;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
