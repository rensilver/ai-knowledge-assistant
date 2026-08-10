package com.rensilver.ai_knowledge_assistant.chat;

import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Persists conversation history in the {@code chat_history} table so it
 * survives restarts, replacing Spring AI's {@code InMemoryChatMemoryRepository}.
 *
 * <p>Written against {@code JdbcTemplate} rather than JPA: this sits on the
 * chat hot path (read + rewrite on every turn) and needs no entity graph,
 * dirty checking, or lazy loading.
 *
 * <p>Conversation ids arriving here are the packed {@link ConversationKey}
 * form; they are unpacked into real {@code user_id} / {@code conversation_id}
 * columns on the way in and repacked on the way out.
 */
@Repository
public class JdbcChatHistoryRepository implements ChatMemoryRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcChatHistoryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<String> findConversationIds() {
        return jdbcTemplate.query(
                "SELECT DISTINCT user_id, conversation_id FROM chat_history",
                (rs, rowNum) -> new ConversationKey(
                        rs.getObject("user_id", java.util.UUID.class),
                        rs.getObject("conversation_id", java.util.UUID.class)
                ).format()
        );
    }

    @Override
    public List<Message> findByConversationId(String conversationId) {
        ConversationKey key = ConversationKey.parse(conversationId);

        return jdbcTemplate.query(
                """
                SELECT message_type, content
                FROM chat_history
                WHERE user_id = ? AND conversation_id = ?
                ORDER BY id
                """,
                (rs, rowNum) -> toMessage(rs.getString("message_type"), rs.getString("content")),
                key.userId(), key.conversationId()
        );
    }

    /**
     * Replaces the stored conversation wholesale, matching the contract Spring
     * AI's own JDBC repository implements: {@code MessageWindowChatMemory}
     * hands back the already-trimmed window, so appending would both duplicate
     * earlier turns and defeat the window.
     */
    @Override
    @Transactional
    public void saveAll(String conversationId, List<Message> messages) {
        ConversationKey key = ConversationKey.parse(conversationId);
        deleteByConversationId(conversationId);

        jdbcTemplate.batchUpdate(
                """
                INSERT INTO chat_history (conversation_id, user_id, message_type, content)
                VALUES (?, ?, ?, ?)
                """,
                messages,
                messages.size(),
                (ps, message) -> {
                    ps.setObject(1, key.conversationId());
                    ps.setObject(2, key.userId());
                    ps.setString(3, message.getMessageType().name());
                    ps.setString(4, message.getText());
                }
        );
    }

    @Override
    public void deleteByConversationId(String conversationId) {
        ConversationKey key = ConversationKey.parse(conversationId);
        jdbcTemplate.update(
                "DELETE FROM chat_history WHERE user_id = ? AND conversation_id = ?",
                key.userId(), key.conversationId()
        );
    }

    /**
     * TOOL messages are persisted as plain assistant turns: this app doesn't
     * replay tool call/response pairs across turns (see {@code AssistantTools}),
     * and rehydrating a {@code ToolResponseMessage} would need the original
     * tool call ids, which aren't stored.
     */
    private Message toMessage(String messageType, String content) {
        return switch (MessageType.valueOf(messageType)) {
            case USER -> new UserMessage(content);
            case SYSTEM -> new SystemMessage(content);
            case ASSISTANT, TOOL -> new AssistantMessage(content);
        };
    }
}
