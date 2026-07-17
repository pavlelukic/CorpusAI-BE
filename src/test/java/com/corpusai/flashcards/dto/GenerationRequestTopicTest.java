package com.corpusai.flashcards.dto;

import com.corpusai.model.ModelProvider;
import com.corpusai.quiz.dto.QuizGenerationRequest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Topic normalisation for both generation endpoints. A whitespace-only topic has to become null
 * rather than reach the services: the RAG query already treated blank as "no topic", but the blank
 * string was still being persisted to flashcard_sets.topic / quizzes.topic and shown in history.
 *
 * Plain unit tests - records normalise in their compact constructor, no Spring context needed.
 */
class GenerationRequestTopicTest {

    @Test
    void blankFlashcardTopicBecomesNull() {
        assertThat(new FlashcardRequest("   ", 5, "en", ModelProvider.OPENAI).topic()).isNull();
        assertThat(new FlashcardRequest("", 5, "en", ModelProvider.OPENAI).topic()).isNull();
        assertThat(new FlashcardRequest("\t\n ", 5, "en", ModelProvider.OPENAI).topic()).isNull();
    }

    @Test
    void nullFlashcardTopicStaysNull() {
        assertThat(new FlashcardRequest(null, 5, "en", ModelProvider.OPENAI).topic()).isNull();
    }

    @Test
    void realFlashcardTopicIsTrimmedButKept() {
        assertThat(new FlashcardRequest("  design patterns  ", 5, "en", ModelProvider.OPENAI).topic())
                .isEqualTo("design patterns");
        assertThat(new FlashcardRequest("design patterns", 5, "en", ModelProvider.OPENAI).topic())
                .isEqualTo("design patterns");
    }

    @Test
    void blankQuizTopicBecomesNull() {
        assertThat(new QuizGenerationRequest("   ", 5, "en", ModelProvider.OPENAI).topic()).isNull();
        assertThat(new QuizGenerationRequest("", 5, "en", ModelProvider.OPENAI).topic()).isNull();
    }

    @Test
    void nullQuizTopicStaysNull() {
        assertThat(new QuizGenerationRequest(null, 5, "en", ModelProvider.OPENAI).topic()).isNull();
    }

    @Test
    void realQuizTopicIsTrimmedButKept() {
        assertThat(new QuizGenerationRequest("  waterfall model  ", 5, "en", ModelProvider.OPENAI).topic())
                .isEqualTo("waterfall model");
    }
}