package com.corpusai.flashcards.dto;

import com.corpusai.flashcards.Difficulty;

public record FlashcardResponse(
        String question,
        String answer,
        Difficulty difficulty,
        String sourceHint
) {}
