package com.corpusai.flashcards;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface FlashcardSetRepository extends JpaRepository<FlashcardSet, UUID> {

    List<FlashcardSet> findAllByUserIdAndSubjectIdOrderByCreatedAtDesc(UUID userId, String subjectId);
}
