package com.rensilver.ai_knowledge_assistant.chat;

import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Resolves the {@code ChatMemory} key for a chat turn. {@code ChatMemory}
 * (see {@code OllamaConfig}) is a single store shared by every user, keyed
 * only by whatever conversation id is passed in — so the key is namespaced
 * with the user's id here to stop one user from reading another's history
 * by guessing/reusing a conversation id.
 */
@Service
public class ConversationMemoryService {

    public ConversationContext resolve(UUID requestedConversationId, UUID userId) {
        UUID conversationId = requestedConversationId != null ? requestedConversationId : UUID.randomUUID();
        String memoryKey = new ConversationKey(userId, conversationId).format();
        return new ConversationContext(conversationId, memoryKey);
    }
}
