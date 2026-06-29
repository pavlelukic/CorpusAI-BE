package com.corpusai.chat;

import dev.langchain4j.service.TokenStream;

interface TutorAssistant {
    TokenStream chat(String userMessage);
}
