package com.corpusai.flashcards;

public record GeneratedFlashcard(
        String question,
        String answer,
        Difficulty difficulty,
        String sourceHint
) {}
