package com.corpusai.chat.dto;

import com.corpusai.chat.MessageRole;

import java.time.Instant;
import java.util.UUID;

public record ChatMessageResponse(UUID id, MessageRole role, String content, Instant createdAt) {}
