package com.corpusai.auth;

import java.util.UUID;

public record AuthenticatedUser(UUID id, Role role) {
}
