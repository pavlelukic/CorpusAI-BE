package com.corpusai.document;

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
@Table(name = "documents")
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "subject_id", nullable = false)
    private String subjectId;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "storage_path", nullable = false)
    private String storagePath;

    @Column(name = "content_hash")
    private String contentHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DocumentStatus status;

    @Column(name = "uploaded_by")
    private UUID uploadedBy;

    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private Instant uploadedAt;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    protected Document() {
        // required by Hibernate
    }

    public Document(String subjectId, String fileName, String storagePath, UUID uploadedBy) {
        this.subjectId = subjectId;
        this.fileName = fileName;
        this.storagePath = storagePath;
        this.uploadedBy = uploadedBy;
        this.status = DocumentStatus.PENDING;
        this.uploadedAt = Instant.now();
    }

    public void markIngesting() {
        this.status = DocumentStatus.INGESTING;
        this.errorMessage = null;
    }

    public void markReady(String contentHash) {
        this.status = DocumentStatus.READY;
        this.contentHash = contentHash;
        this.errorMessage = null;
    }

    public void markFailed(String errorMessage) {
        this.status = DocumentStatus.FAILED;
        this.errorMessage = errorMessage;
    }

    public UUID getId() {
        return id;
    }

    public String getSubjectId() {
        return subjectId;
    }

    public String getFileName() {
        return fileName;
    }

    public String getStoragePath() {
        return storagePath;
    }

    public String getContentHash() {
        return contentHash;
    }

    public DocumentStatus getStatus() {
        return status;
    }

    public UUID getUploadedBy() {
        return uploadedBy;
    }

    public Instant getUploadedAt() {
        return uploadedAt;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
