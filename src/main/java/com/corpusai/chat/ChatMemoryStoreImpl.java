package com.corpusai.chat;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class ChatMemoryStoreImpl implements ChatMemoryStore {

    private final ChatMessageRepository chatMessageRepository;

    public ChatMemoryStoreImpl(ChatMessageRepository chatMessageRepository) {
        this.chatMessageRepository = chatMessageRepository;
    }

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        UUID sessionId = (UUID) memoryId;
        return chatMessageRepository.findAllBySessionIdOrderByCreatedAtAsc(sessionId).stream()
                .map(this::toLangChainMessage)
                .toList();
    }

    // MessageWindowChatMemory.add() always passes the full sliding window here, not just
    // the new message - persisting that verbatim would silently drop history beyond the
    // window and break the "full transcript" history endpoint. Eviction only ever trims
    // from the front, so the last element is always exactly the message just added;
    // appending only that keeps chat_messages a complete, append-only transcript.
    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        if (messages.isEmpty()) {
            return;
        }
        UUID sessionId = (UUID) memoryId;
        ChatMessage last = messages.get(messages.size() - 1);
        if (last instanceof UserMessage userMessage) {
            chatMessageRepository.save(new com.corpusai.chat.ChatMessage(sessionId, MessageRole.USER, userMessage.singleText()));
        } else if (last instanceof AiMessage aiMessage) {
            chatMessageRepository.save(new com.corpusai.chat.ChatMessage(sessionId, MessageRole.ASSISTANT, aiMessage.text()));
        }
        // SystemMessage (the per-request system prompt also flows through chatMemory.add())
        // and any other message type are deliberately not part of the persisted transcript.
    }

    @Override
    public void deleteMessages(Object memoryId) {
        UUID sessionId = (UUID) memoryId;
        chatMessageRepository.deleteAll(chatMessageRepository.findAllBySessionIdOrderByCreatedAtAsc(sessionId));
    }

    private ChatMessage toLangChainMessage(com.corpusai.chat.ChatMessage message) {
        return message.getRole() == MessageRole.USER
                ? UserMessage.from(message.getContent())
                : AiMessage.from(message.getContent());
    }
}
