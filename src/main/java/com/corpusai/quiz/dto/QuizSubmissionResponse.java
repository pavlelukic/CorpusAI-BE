package com.corpusai.quiz.dto;

import java.util.List;
import java.util.UUID;

public record QuizSubmissionResponse(
        int score,
        int total,
        List<AnswerResult> results
) {
    public record AnswerResult(
            UUID questionId,
            boolean correct,
            int correctIndex,
            String explanation
    ) {}
}
