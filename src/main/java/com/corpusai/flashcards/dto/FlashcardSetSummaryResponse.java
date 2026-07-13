package com.corpusai.flashcards.dto;

import com.corpusai.model.ModelProvider;

import java.time.Instant;
import java.util.UUID;

public record FlashcardSetSummaryResponse(
        UUID setId,
        String subjectId,
        String topic,
        String lang,
        ModelProvider provider,
        Instant createdAt
) {}
