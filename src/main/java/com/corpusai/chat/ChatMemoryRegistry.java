package com.corpusai.chat;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
public class ChatMemoryRegistry {

    private static final int MAX_MESSAGES = 20;

    private final Cache<UUID, ChatMemory> sessions = Caffeine.newBuilder()
            .expireAfterAccess(1, TimeUnit.HOURS)
            .build();

    private final ChatMemoryStore chatMemoryStore;

    public ChatMemoryRegistry(ChatMemoryStore chatMemoryStore) {
        this.chatMemoryStore = chatMemoryStore;
    }

    public ChatMemory getOrCreate(UUID sessionId) {
        return sessions.get(sessionId, id -> MessageWindowChatMemory.builder()
                .id(id)
                .maxMessages(MAX_MESSAGES)
                .chatMemoryStore(chatMemoryStore)
                .build());
    }
}
