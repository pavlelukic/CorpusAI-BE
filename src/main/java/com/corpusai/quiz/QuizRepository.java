package com.corpusai.quiz;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface QuizRepository extends JpaRepository<Quiz, UUID> {

    List<Quiz> findAllByUserIdAndSubjectIdOrderByCreatedAtDesc(UUID userId, String subjectId);
}
