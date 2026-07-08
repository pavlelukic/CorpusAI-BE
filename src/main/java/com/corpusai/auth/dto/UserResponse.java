package com.corpusai.auth.dto;

import com.corpusai.auth.Role;

import java.util.UUID;

public record UserResponse(UUID id, String email, String displayName, Role role) {}
