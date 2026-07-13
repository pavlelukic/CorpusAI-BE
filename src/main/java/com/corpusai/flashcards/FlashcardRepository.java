package com.corpusai.flashcards;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface FlashcardRepository extends JpaRepository<Flashcard, UUID> {

    List<Flashcard> findAllBySetIdOrderByPositionAsc(UUID setId);
}
