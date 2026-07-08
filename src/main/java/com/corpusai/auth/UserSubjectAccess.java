package com.corpusai.auth;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_subjects")
public class UserSubjectAccess {

    @EmbeddedId
    private UserSubjectId id;

    @Column(name = "granted_at", nullable = false)
    private Instant grantedAt;

    @Column(name = "granted_by")
    private UUID grantedBy;

    protected UserSubjectAccess() {
        // required by Hibernate
    }

    public UserSubjectAccess(UserSubjectId id, UUID grantedBy) {
        this.id = id;
        this.grantedBy = grantedBy;
        this.grantedAt = Instant.now();
    }

    public UserSubjectId getId() {
        return id;
    }

    public Instant getGrantedAt() {
        return grantedAt;
    }

    public UUID getGrantedBy() {
        return grantedBy;
    }
}
