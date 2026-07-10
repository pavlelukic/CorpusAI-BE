package com.corpusai.chat;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ChatSessionRepository extends JpaRepository<ChatSession, UUID> {

    List<ChatSession> findAllByUserIdAndSubjectIdOrderByUpdatedAtDesc(UUID userId, String subjectId);
}
