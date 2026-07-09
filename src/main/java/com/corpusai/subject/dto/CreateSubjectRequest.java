package com.corpusai.subject.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateSubjectRequest(
        @NotBlank @Size(max = 255) String displayName,
        @NotBlank @Size(max = 255) String displayNameSr,
        @Size(max = 10000) String systemPrompt
) {}
