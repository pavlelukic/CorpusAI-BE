package com.corpusai.quiz.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record QuizSubmissionRequest(
        @NotEmpty(message = "answers must not be empty") List<@Valid AnswerSubmission> answers
) {
    public record AnswerSubmission(
            @NotNull(message = "questionId is required") UUID questionId,
            @NotNull(message = "selectedIndex is required")
            @Min(value = 0, message = "selectedIndex must be between 0 and 3")
            @Max(value = 3, message = "selectedIndex must be between 0 and 3") Integer selectedIndex
    ) {}
}
