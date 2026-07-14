package com.corpusai.quiz.dto;

import com.corpusai.model.ModelProvider;

import java.time.Instant;
import java.util.UUID;

public record QuizSummaryResponse(
        UUID quizId,
        String subjectId,
        String topic,
        String lang,
        ModelProvider provider,
        int questionCount,
        Integer score,
        Instant completedAt,
        Instant createdAt
) {}
