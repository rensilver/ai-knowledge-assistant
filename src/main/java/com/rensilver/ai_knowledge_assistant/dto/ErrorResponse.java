package com.rensilver.ai_knowledge_assistant.dto;

import java.time.Instant;
import java.util.List;

/**
 * Uniform error body returned by {@code GlobalExceptionHandler} for every
 * failed request, so the frontend can rely on a single shape regardless of
 * which endpoint or exception produced it.
 *
 * @param status  HTTP status code, e.g. 409
 * @param error   HTTP reason phrase, e.g. "Conflict"
 * @param message human-readable summary of what went wrong
 * @param path    request URI that triggered the error
 * @param details field-level validation messages, if any (empty otherwise)
 */
public record ErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        List<String> details
) {
    public ErrorResponse(int status, String error, String message, String path, List<String> details) {
        this(Instant.now(), status, error, message, path, details);
    }
}
