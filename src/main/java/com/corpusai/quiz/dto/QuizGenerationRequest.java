package com.corpusai.quiz.dto;

import com.corpusai.model.ModelProvider;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record QuizGenerationRequest(
        @Size(max = 200, message = "topic must be at most 200 characters") String topic,
        @Min(value = 1, message = "count must be at least 1")
        @Max(value = 20, message = "count must be at most 20") Integer count,
        @Pattern(regexp = "en|sr", message = "lang must be 'en' or 'sr'") String lang,
        ModelProvider provider
) {}
