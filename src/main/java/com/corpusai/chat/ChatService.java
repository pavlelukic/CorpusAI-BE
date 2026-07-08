package com.corpusai.chat;

import com.corpusai.model.ModelFactory;
import com.corpusai.model.ModelProvider;
import com.corpusai.subject.Subject;
import com.corpusai.subject.SubjectService;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.TokenStream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class ChatService {

    private final ModelFactory modelFactory;
    private final RetrievalAugmentorFactory retrievalAugmentorFactory;
    private final ChatMemoryRegistry chatMemoryRegistry;
    private final SubjectService subjectService;

    public ChatService(ModelFactory modelFactory,
                       RetrievalAugmentorFactory retrievalAugmentorFactory,
                       ChatMemoryRegistry chatMemoryRegistry,
                       SubjectService subjectService) {
        this.modelFactory = modelFactory;
        this.retrievalAugmentorFactory = retrievalAugmentorFactory;
        this.chatMemoryRegistry = chatMemoryRegistry;
        this.subjectService = subjectService;
    }

    public TokenStream process(String subjectId, String sessionId, String message, String lang) {
        Subject subject = subjectService.findById(subjectId);

        log.info("Chat request - subject: '{}', session: '{}', lang: '{}'", subjectId, sessionId, lang);

        var assistant = AiServices.builder(TutorAssistant.class)
                .streamingChatModel(modelFactory.streamingChatModel(ModelProvider.OPENAI, "gpt-4o-mini"))
                .systemMessageProvider(memoryId -> buildSystemPrompt(subject, lang))
                .chatMemory(chatMemoryRegistry.getOrCreate(sessionId))
                .retrievalAugmentor(retrievalAugmentorFactory.forSubject(subjectId))
                .build();

        return assistant.chat(message);
    }

    private String buildSystemPrompt(Subject subject, String lang) {
        String base = subjectService.systemPromptFor(subject);
        String langInstruction = "sr".equals(lang)
                ? "\nAlways respond in Serbian (Latin script, not Cyrillic), regardless of the language the User writes in."
                : "\nAlways respond in English, regardless of the language the User writes in.";

        return base + langInstruction;
    }

}
