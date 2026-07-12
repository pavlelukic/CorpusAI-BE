package com.corpusai.chat;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {

    List<ChatMessage> findAllBySessionIdOrderByCreatedAtAsc(UUID sessionId);

    Optional<ChatMessage> findFirstBySessionIdOrderByCreatedAtDesc(UUID sessionId);
}
