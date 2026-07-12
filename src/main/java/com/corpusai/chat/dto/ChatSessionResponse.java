package com.corpusai.chat.dto;

import com.corpusai.model.ModelProvider;

import java.time.Instant;
import java.util.UUID;

public record ChatSessionResponse(
        UUID id,
        String title,
        String subjectId,
        String lang,
        ModelProvider provider,
        Instant createdAt
) {}
