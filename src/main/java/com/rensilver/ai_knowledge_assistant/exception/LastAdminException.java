package com.rensilver.ai_knowledge_assistant.exception;

/**
 * Thrown by UserService when a role change would leave zero ADMIN users.
 * Mapped to HTTP 409 by {@link GlobalExceptionHandler}.
 */
public class LastAdminException extends RuntimeException {
    public LastAdminException() {
        super("Cannot change role: this is the last remaining admin");
    }
}
