package com.rensilver.ai_knowledge_assistant.chat;

import java.util.UUID;

/**
 * The identity of one conversation: which user owns it and which of their
 * conversations it is.
 *
 * <p>Spring AI's {@code ChatMemory} API addresses a conversation by a single
 * opaque string, so both halves are packed into one key
 * ({@code "<userId>:<conversationId>"}). Namespacing by user is what stops one
 * user from reading another's history by guessing a conversation id, since the
 * underlying memory store is shared by every user.
 *
 * <p>{@code JdbcChatHistoryRepository} unpacks the key back into two real
 * columns when persisting.
 */
public record ConversationKey(UUID userId, UUID conversationId) {

    private static final String SEPARATOR = ":";

    /** The opaque key handed to {@code ChatMemory}. */
    public String format() {
        return userId + SEPARATOR + conversationId;
    }

    public static ConversationKey parse(String key) {
        int separatorAt = key.indexOf(SEPARATOR);
        if (separatorAt < 0) {
            throw new IllegalArgumentException("Malformed conversation key: " + key);
        }

        try {
            return new ConversationKey(
                    UUID.fromString(key.substring(0, separatorAt)),
                    UUID.fromString(key.substring(separatorAt + 1))
            );
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Malformed conversation key: " + key, e);
        }
    }
}
