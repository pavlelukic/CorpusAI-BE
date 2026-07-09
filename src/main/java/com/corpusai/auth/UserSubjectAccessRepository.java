package com.corpusai.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

public interface UserSubjectAccessRepository extends JpaRepository<UserSubjectAccess, UserSubjectId> {

    List<UserSubjectAccess> findAllByIdUserId(UUID userId);

    boolean existsByIdUserIdAndIdSubjectId(UUID userId, String subjectId);

    @Transactional
    void deleteAllByIdSubjectId(String subjectId);
}
