package com.corpusai.chat.dto;

import com.corpusai.model.ModelProvider;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record CreateChatSessionRequest(
        @NotBlank String subjectId,
        @NotBlank @Pattern(regexp = "en|sr", message = "lang must be 'en' or 'sr'") String lang,
        @NotNull ModelProvider provider
) {}
