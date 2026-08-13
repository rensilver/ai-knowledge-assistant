package com.rensilver.ai_knowledge_assistant.exception;

import java.util.UUID;

public class UserNotFoundException extends RuntimeException {
    public UserNotFoundException(UUID id) {
        super("No user found with id: " + id);
    }
}
