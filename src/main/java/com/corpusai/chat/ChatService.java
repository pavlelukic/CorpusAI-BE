package com.corpusai.chat;

import com.corpusai.config.SubjectsProperties;
import com.corpusai.model.ModelFactory;
import com.corpusai.model.ModelProvider;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.TokenStream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Slf4j
@Service
public class ChatService {

    private final ModelFactory modelFactory;
    private final RetrievalAugmentorFactory retrievalAugmentorFactory;
    private final ChatMemoryRegistry chatMemoryRegistry;
    private final SubjectsProperties subjectsProperties;

    public ChatService(ModelFactory modelFactory,
                       RetrievalAugmentorFactory retrievalAugmentorFactory,
                       ChatMemoryRegistry chatMemoryRegistry,
                       SubjectsProperties subjectsProperties) {
        this.modelFactory = modelFactory;
        this.retrievalAugmentorFactory = retrievalAugmentorFactory;
        this.chatMemoryRegistry = chatMemoryRegistry;
        this.subjectsProperties = subjectsProperties;
    }

    public TokenStream process(String subjectId, String sessionId, String message, String lang) {
        var subject = subjectsProperties.subjects().stream()
                .filter(s -> s.id().equals(subjectId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown Subject: " + subjectId));

        log.info("Chat request - subject: '{}', session: '{}', lang: '{}'", subjectId, sessionId, lang);

        var assistant = AiServices.builder(TutorAssistant.class)
                .streamingChatModel(modelFactory.streamingChatModel(ModelProvider.OPENAI, "gpt-4o-mini"))
                .systemMessageProvider(memoryId -> buildSystemPrompt(subject.systemPromptPath(), lang))
                .chatMemory(chatMemoryRegistry.getOrCreate(sessionId))
                .retrievalAugmentor(retrievalAugmentorFactory.forSubject(subjectId))
                .build();

        return assistant.chat(message);
    }

    private String loadPrompt(String classPath) {
        try {
            return new ClassPathResource(classPath).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Could not load System Prompt: " + classPath, ex);
        }
    }

    private String buildSystemPrompt(String classPath, String lang) {
        String base = loadPrompt(classPath);
        String langInstruction = "sr".equals(lang)
                ? "\nAlways respond in Serbian (Latin script, not Cyrillic), regardless of the language the User writes in."
                : "\nAlways respond in English, regardless of the language the User writes in.";

        return base + langInstruction;
    }

}
