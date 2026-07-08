package com.corpusai.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "corpusai.jwt")
public record JwtProperties(String secret, long expirationHours) {
}
