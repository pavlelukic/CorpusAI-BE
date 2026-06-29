package com.corpusai.chat;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class ChatMemoryRegistry {
    private final Cache<String, ChatMemory> sessions = Caffeine.newBuilder()
            .expireAfterAccess(1, TimeUnit.HOURS)
            .build();

    public ChatMemory getOrCreate(String sessionId) {
        return sessions.get(sessionId, id -> MessageWindowChatMemory.builder()
                .id(id)
                .maxMessages(20)
                .build());
    }
}
