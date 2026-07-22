package com.corpusai.chat.dto;

import com.corpusai.chat.MessageRole;

import java.time.Instant;
import java.util.UUID;

// The four usage fields are all nullable and must stay that way: USER messages never have usage,
// assistant replies stored before the usage row carried a message id have none, and a provider that
// reports no token counts still yields a row with a real latencyMs and null tokens.
public record ChatMessageResponse(UUID id, MessageRole role, String content, Instant createdAt,
                                  Integer inputTokens, Integer outputTokens, Long latencyMs, String model) {}
