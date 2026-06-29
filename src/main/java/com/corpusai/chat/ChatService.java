package com.corpusai.chat;

import com.corpusai.config.SubjectsProperties;
import com.corpusai.model.ModelFactory;
import com.corpusai.model.ModelProvider;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.TokenStream;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

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

    public TokenStream process(String subjectId, String sessionId, String message) {
        var subject = subjectsProperties.subjects().stream()
                .filter(s -> s.id().equals(subjectId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown Subject: " + subjectId));

        var assistant = AiServices.builder(TutorAssistant.class)
                .streamingChatModel(modelFactory.streamingChatModel(ModelProvider.OPENAI, "gpt-4o-mini"))
                .systemMessageProvider(memoryId -> loadPrompt(subject.systemPromptPath()))
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

}
