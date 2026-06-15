package com.corpusai.subject;

public record Subject(
        String id,
        String displayName,
        String displayNameSr,
        String systemPromptPath,
        String documentsPath
) {
}
