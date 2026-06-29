package com.corpusai.quiz;

public record Flashcard (
        String question,
        String answer,
        Difficulty difficulty,
        String sourceHint
){}
