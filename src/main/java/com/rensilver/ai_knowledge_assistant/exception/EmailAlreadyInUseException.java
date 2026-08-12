package com.rensilver.ai_knowledge_assistant.exception;

/**
 * Thrown by AuthService when registering with an email that's already taken.
 * Mapped to HTTP 409 by {@link GlobalExceptionHandler}.
 */
public class EmailAlreadyInUseException extends RuntimeException {
    public EmailAlreadyInUseException(String email) {
        super("Email already in use: " + email);
    }
}
