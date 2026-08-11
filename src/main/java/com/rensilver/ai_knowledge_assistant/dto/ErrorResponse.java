package com.rensilver.ai_knowledge_assistant.dto;

import io.swagger.v3.oas.annotations.media.Schema;

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
        @Schema(description = "HTTP status code, e.g. 409")
        int status,
        @Schema(description = "HTTP reason phrase, e.g. \"Conflict\"")
        String error,
        @Schema(description = "human-readable summary of what went wrong")
        String message,
        @Schema(description = "request URI that triggered the error")
        String path,
        @Schema(description = "field-level validation messages, if any (empty otherwise)")
        List<String> details
) {
    public ErrorResponse(int status, String error, String message, String path, List<String> details) {
        this(Instant.now(), status, error, message, path, details);
    }
}
