package com.rensilver.ai_knowledge_assistant.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

/**
 * Payload for POST /chat.
 *
 * @param message        the user's question
 * @param conversationId omit to start a new conversation; pass back the
 *                       value from a previous {@link ChatResponse} to
 *                       continue one with memory of prior turns
 */
public record ChatRequest(

        @NotBlank(message = "Message is required")
        String message,

        UUID conversationId
) {
}
