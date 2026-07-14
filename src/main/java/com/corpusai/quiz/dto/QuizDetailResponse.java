package com.corpusai.quiz.dto;

import com.corpusai.model.ModelProvider;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record QuizDetailResponse(
        UUID quizId,
        String subjectId,
        String topic,
        String lang,
        ModelProvider provider,
        int questionCount,
        Integer score,
        Instant completedAt,
        Instant createdAt,
        List<QuestionDetail> questions
) {
    /**
     * The grading fields (selectedIndex, correct, correctIndex, explanation) are populated only
     * once the quiz is completed; before that they stay null and are omitted from the JSON, so a
     * pre-completion GET can never reveal the correct answers.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record QuestionDetail(
            UUID id,
            String question,
            List<String> options,
            Integer selectedIndex,
            Boolean correct,
            Integer correctIndex,
            String explanation
    ) {}
}
