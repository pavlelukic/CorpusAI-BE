package com.corpusai.chat.dto;

import java.util.UUID;

public record ChatDoneResponse(UUID messageId, Integer inputTokens, Integer outputTokens, long latencyMs) {}
