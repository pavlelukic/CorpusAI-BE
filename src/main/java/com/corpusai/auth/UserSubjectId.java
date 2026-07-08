package com.corpusai.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class UserSubjectId implements Serializable {

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "subject_id")
    private String subjectId;

    protected UserSubjectId() {
        // required by Hibernate
    }

    public UserSubjectId(UUID userId, String subjectId) {
        this.userId = userId;
        this.subjectId = subjectId;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getSubjectId() {
        return subjectId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UserSubjectId that)) return false;
        return Objects.equals(userId, that.userId) && Objects.equals(subjectId, that.subjectId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, subjectId);
    }
}
