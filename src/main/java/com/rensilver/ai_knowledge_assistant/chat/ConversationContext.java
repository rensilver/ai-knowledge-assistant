package com.rensilver.ai_knowledge_assistant.chat;

import java.util.UUID;

/**
 * @param conversationId public id to echo back to the client
 * @param memoryKey       key actually used to namespace entries in the
 *                        shared {@code ChatMemory} store (see
 *                        {@code ConversationMemoryService})
 */
public record ConversationContext(UUID conversationId, String memoryKey) {
}
