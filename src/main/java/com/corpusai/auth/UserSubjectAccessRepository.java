package com.corpusai.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface UserSubjectAccessRepository extends JpaRepository<UserSubjectAccess, UserSubjectId> {

    List<UserSubjectAccess> findAllByIdUserId(UUID userId);

    boolean existsByIdUserIdAndIdSubjectId(UUID userId, String subjectId);
}
