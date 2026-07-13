package com.corpusai.flashcards.dto;

import com.corpusai.model.ModelProvider;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record FlashcardSetResponse(
        UUID setId,
        String subjectId,
        String topic,
        String lang,
        ModelProvider provider,
        Instant createdAt,
        List<FlashcardResponse> cards
) {}
