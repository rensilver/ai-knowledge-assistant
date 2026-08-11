package com.rensilver.ai_knowledge_assistant.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

/**
 * Returned by POST /chat.
 *
 * @param answer         the grounded answer produced by the LLM
 * @param conversationId echo this back on the next request to continue the
 *                       same conversation with memory of this turn
 * @param sources        document pages the answer was grounded in (empty if
 *                       no relevant context was found)
 */
public record ChatResponse(
        @Schema(description = "the grounded answer produced by the LLM")
        String answer,
        @Schema(description = "echo this back on the next request to continue the same "
                + "conversation with memory of this turn")
        UUID conversationId,
        @Schema(description = "document pages the answer was grounded in (empty if no relevant "
                + "context was found)")
        List<SourceReference> sources
) {
}
