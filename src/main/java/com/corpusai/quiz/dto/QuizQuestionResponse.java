package com.corpusai.quiz.dto;

import java.util.List;
import java.util.UUID;

/**
 * A question as the quiz taker sees it: never carries correctIndex or explanation.
 * The id is exposed deliberately — submit answers by question id.
 */
public record QuizQuestionResponse(
        UUID id,
        String question,
        List<String> options
) {}
