package com.rensilver.ai_knowledge_assistant.exception;

/**
 * Thrown by AuthService when registering with an email that's already taken.
 * Map this to HTTP 409 in your global @ExceptionHandler (not built yet).
 */
public class EmailAlreadyInUseException extends RuntimeException {
    public EmailAlreadyInUseException(String email) {
        super("Email already in use: " + email);
    }
}
