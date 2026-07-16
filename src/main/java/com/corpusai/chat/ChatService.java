package com.corpusai.chat;

import com.corpusai.auth.AuthenticatedUser;
import com.corpusai.auth.SubjectAccessService;
import com.corpusai.model.ModelFactory;
import com.corpusai.model.ModelProvider;
import com.corpusai.rag.RetrievalAugmentorFactory;
import com.corpusai.subject.Subject;
import com.corpusai.subject.SubjectService;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.TokenStream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class ChatService {

    private static final int TITLE_MAX_LENGTH = 60;

    private final ModelFactory modelFactory;
    private final RetrievalAugmentorFactory retrievalAugmentorFactory;
    private final ChatMemoryRegistry chatMemoryRegistry;
    private final SubjectService subjectService;
    private final SubjectAccessService subjectAccessService;
    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;

    public ChatService(ModelFactory modelFactory,
                       RetrievalAugmentorFactory retrievalAugmentorFactory,
                       ChatMemoryRegistry chatMemoryRegistry,
                       SubjectService subjectService,
                       SubjectAccessService subjectAccessService,
                       ChatSessionRepository chatSessionRepository,
                       ChatMessageRepository chatMessageRepository) {
        this.modelFactory = modelFactory;
        this.retrievalAugmentorFactory = retrievalAugmentorFactory;
        this.chatMemoryRegistry = chatMemoryRegistry;
        this.subjectService = subjectService;
        this.subjectAccessService = subjectAccessService;
        this.chatSessionRepository = chatSessionRepository;
        this.chatMessageRepository = chatMessageRepository;
    }

    public ChatSession createSession(AuthenticatedUser principal, String subjectId, String lang, ModelProvider provider) {
        subjectService.findById(subjectId);
        subjectAccessService.checkAccess(principal, subjectId);

        ChatSession session = new ChatSession(principal.id(), subjectId, lang, provider);
        return chatSessionRepository.save(session);
    }

    public ProcessResult process(AuthenticatedUser principal, UUID sessionId, String message) {
        ChatSession session = resolveOwnedSession(principal, sessionId);
        subjectAccessService.checkAccess(principal, session.getSubjectId());
        Subject subject = subjectService.findById(session.getSubjectId());

        if (session.getTitle() == null) {
            session.assignTitle(truncateTitle(message));
        }
        session.touch();
        chatSessionRepository.save(session);

        log.info("Chat request - session: '{}', subject: '{}', provider: '{}'",
                sessionId, session.getSubjectId(), session.getProvider());

        String model = modelFactory.chatModelName(session.getProvider());
        var assistant = AiServices.builder(TutorAssistant.class)
                .streamingChatModel(modelFactory.streamingChatModel(session.getProvider(), model))
                .systemMessageProvider(memoryId -> buildSystemPrompt(subject, session.getLang()))
                .chatMemory(chatMemoryRegistry.getOrCreate(sessionId))
                .retrievalAugmentor(retrievalAugmentorFactory.forSubject(session.getSubjectId()))
                // We persist only what the user actually typed
                .storeRetrievedContentInChatMemory(false)
                .build();

        TokenStream tokenStream = assistant.chat(message);
        return new ProcessResult(tokenStream, session.getSubjectId(), session.getProvider(), model);
    }

    public record ProcessResult(TokenStream tokenStream, String subjectId, ModelProvider provider, String model) {
    }

    public UUID latestMessageId(UUID sessionId) {
        return chatMessageRepository.findFirstBySessionIdOrderByCreatedAtDesc(sessionId)
                .map(ChatMessage::getId)
                .orElse(null);
    }

    public List<ChatSession> listSessions(AuthenticatedUser principal, String subjectId) {
        subjectService.findById(subjectId);
        return chatSessionRepository.findAllByUserIdAndSubjectIdOrderByUpdatedAtDesc(principal.id(), subjectId);
    }

    public List<ChatMessage> getTranscript(AuthenticatedUser principal, UUID sessionId) {
        ChatSession session = resolveOwnedSession(principal, sessionId);
        return chatMessageRepository.findAllBySessionIdOrderByCreatedAtAsc(session.getId());
    }

    public void deleteSession(AuthenticatedUser principal, UUID sessionId) {
        ChatSession session = resolveOwnedSession(principal, sessionId);
        chatSessionRepository.delete(session);
    }

    private ChatSession resolveOwnedSession(AuthenticatedUser principal, UUID sessionId) {
        ChatSession session = chatSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ChatSessionNotFoundException("Unknown chat session: " + sessionId));
        if (!session.getUserId().equals(principal.id())) {
            throw new AccessDeniedException("You do not have access to this chat session: " + sessionId);
        }
        return session;
    }

    private String truncateTitle(String message) {
        String trimmed = message.trim();
        return trimmed.length() <= TITLE_MAX_LENGTH ? trimmed : trimmed.substring(0, TITLE_MAX_LENGTH);
    }

    private String buildSystemPrompt(Subject subject, String lang) {
        String base = subjectService.systemPromptFor(subject);
        String langInstruction = "sr".equals(lang)
                ? "\nAlways respond in Serbian (Latin script, not Cyrillic), regardless of the language the User writes in."
                : "\nAlways respond in English, regardless of the language the User writes in.";

        return base + langInstruction;
    }

}
