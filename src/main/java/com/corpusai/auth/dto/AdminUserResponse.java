package com.corpusai.auth.dto;

import com.corpusai.auth.Role;

import java.util.List;
import java.util.UUID;

public record AdminUserResponse(UUID id, String email, String displayName, Role role, List<String> subjectIds) {}
