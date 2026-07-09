package com.corpusai.subject.dto;

import java.time.Instant;

public record AdminSubjectResponse(
        String id,
        String displayName,
        String displayNameSr,
        String systemPrompt,
        boolean archived,
        Instant createdAt
) {}
