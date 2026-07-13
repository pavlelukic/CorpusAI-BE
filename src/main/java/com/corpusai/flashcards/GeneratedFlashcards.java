package com.corpusai.flashcards;

import java.util.List;

/**
 * Wrapper around the generated cards. AiServices returns this POJO rather than a bare
 * {@code List<GeneratedFlashcard>} so the Anthropic (prompt-based JSON) path can format and
 * parse it — langchain4j's collection format-instructions path is unimplemented and throws.
 */
public record GeneratedFlashcards(List<GeneratedFlashcard> cards) {}
