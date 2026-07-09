package com.corpusai.document.dto;

import com.corpusai.document.DocumentStatus;

import java.time.Instant;
import java.util.UUID;

public record DocumentResponse(UUID id, String fileName, DocumentStatus status, Instant uploadedAt, String errorMessage) {}
