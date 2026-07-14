package com.corpusai.quiz;

import java.util.List;

/**
 * Wrapper around the generated questions. AiServices returns this POJO rather than a bare
 * {@code List<GeneratedQuestion>} so the Anthropic (prompt-based JSON) path can format and
 * parse it — langchain4j's collection format-instructions path is unimplemented and throws.
 */
public record GeneratedQuiz(List<GeneratedQuestion> questions) {}
