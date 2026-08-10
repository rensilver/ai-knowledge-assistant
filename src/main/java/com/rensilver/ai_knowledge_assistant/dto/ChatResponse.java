package com.rensilver.ai_knowledge_assistant.dto;

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
        String answer,
        UUID conversationId,
        List<SourceReference> sources
) {
}
