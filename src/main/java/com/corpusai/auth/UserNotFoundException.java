package com.corpusai.auth;

import java.util.UUID;

public class UserNotFoundException extends RuntimeException {

    public UserNotFoundException(UUID userId) {
        super("Unknown user: " + userId);
    }
}