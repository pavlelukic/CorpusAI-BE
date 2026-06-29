package com.corpusai.chat;

import dev.langchain4j.service.TokenStream;

public interface TutorAssistant {
    TokenStream chat(String userMessage);
}
