package com.corpusai.quiz.dto;

import com.corpusai.model.ModelProvider;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record QuizResponse(
        UUID quizId,
        String subjectId,
        String topic,
        String lang,
        ModelProvider provider,
        Instant createdAt,
        List<QuizQuestionResponse> questions
) {}
