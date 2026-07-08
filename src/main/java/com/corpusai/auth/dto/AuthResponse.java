package com.corpusai.auth.dto;

public record AuthResponse(String token, UserResponse user) {}
