package com.corpusai.subject;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "subjects")
public class Subject {

    @Id
    private String id;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "display_name_sr", nullable = false)
    private String displayNameSr;

    @Column(name = "system_prompt", columnDefinition = "TEXT")
    private String systemPrompt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private boolean archived;

    protected Subject() {
        // required by Hibernate
    }

    public Subject(String id, String displayName, String displayNameSr, String systemPrompt) {
        this.id = id;
        this.displayName = displayName;
        this.displayNameSr = displayNameSr;
        this.systemPrompt = systemPrompt;
        this.createdAt = Instant.now();
    }

    public void updateDetails(String displayName, String displayNameSr, String systemPrompt) {
        this.displayName = displayName;
        this.displayNameSr = displayNameSr;
        this.systemPrompt = systemPrompt;
    }

    public void archive() {
        this.archived = true;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDisplayNameSr() {
        return displayNameSr;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public boolean isArchived() {
        return archived;
    }
}
