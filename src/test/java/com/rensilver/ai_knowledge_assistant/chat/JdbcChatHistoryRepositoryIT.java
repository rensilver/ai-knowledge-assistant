package com.rensilver.ai_knowledge_assistant.chat;

import com.rensilver.ai_knowledge_assistant.PgVectorTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the repository against a real Postgres with the real Flyway
 * migrations applied — which also proves V1–V5 still apply cleanly.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JdbcChatHistoryRepository.class)
class JdbcChatHistoryRepositoryIT extends PgVectorTestSupport {

    @Autowired
    private JdbcChatHistoryRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID userId;
    private UUID conversationId;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM chat_history");
        jdbcTemplate.update("DELETE FROM users");

        userId = UUID.randomUUID();
        conversationId = UUID.randomUUID();
        insertUser(userId, "owner@example.com");
    }

    private void insertUser(UUID id, String email) {
        jdbcTemplate.update(
                "INSERT INTO users (id, name, email, password, role) VALUES (?, ?, ?, ?, 'USER')",
                id, "Test User", email, "irrelevant-hash"
        );
    }

    private String keyFor(UUID user, UUID conversation) {
        return new ConversationKey(user, conversation).format();
    }

    @Test
    void persistsAndReplaysAConversationInOrder() {
        String key = keyFor(userId, conversationId);

        repository.saveAll(key, List.of(
                new UserMessage("What is our deployment process?"),
                new AssistantMessage("It is documented in the runbook.")
        ));

        List<Message> replayed = repository.findByConversationId(key);

        assertThat(replayed).extracting(Message::getText).containsExactly(
                "What is our deployment process?",
                "It is documented in the runbook."
        );
        assertThat(replayed.get(0)).isInstanceOf(UserMessage.class);
        assertThat(replayed.get(1)).isInstanceOf(AssistantMessage.class);
    }

    @Test
    void saveAllReplacesRatherThanAppends() {
        String key = keyFor(userId, conversationId);

        repository.saveAll(key, List.of(new UserMessage("first")));
        // MessageWindowChatMemory re-saves the whole trimmed window each turn,
        // so appending here would duplicate every earlier message.
        repository.saveAll(key, List.of(new UserMessage("first"), new AssistantMessage("second")));

        assertThat(repository.findByConversationId(key))
                .extracting(Message::getText)
                .containsExactly("first", "second");
    }

    @Test
    void doesNotLeakHistoryBetweenUsersSharingAConversationId() {
        UUID otherUserId = UUID.randomUUID();
        insertUser(otherUserId, "intruder@example.com");

        repository.saveAll(keyFor(userId, conversationId), List.of(new UserMessage("owner secret")));

        // Same conversation id, different user: must see nothing.
        assertThat(repository.findByConversationId(keyFor(otherUserId, conversationId))).isEmpty();
    }

    @Test
    void deletesOnlyTheTargetedConversation() {
        UUID otherConversationId = UUID.randomUUID();
        repository.saveAll(keyFor(userId, conversationId), List.of(new UserMessage("doomed")));
        repository.saveAll(keyFor(userId, otherConversationId), List.of(new UserMessage("kept")));

        repository.deleteByConversationId(keyFor(userId, conversationId));

        assertThat(repository.findByConversationId(keyFor(userId, conversationId))).isEmpty();
        assertThat(repository.findByConversationId(keyFor(userId, otherConversationId)))
                .extracting(Message::getText)
                .containsExactly("kept");
    }

    @Test
    void listsStoredConversationsInTheirPackedForm() {
        String key = keyFor(userId, conversationId);
        repository.saveAll(key, List.of(new UserMessage("hello")));

        assertThat(repository.findConversationIds()).containsExactly(key);
    }

    @Test
    void discardsHistoryWhenItsUserIsDeleted() {
        repository.saveAll(keyFor(userId, conversationId), List.of(new UserMessage("transient")));

        jdbcTemplate.update("DELETE FROM users WHERE id = ?", userId);

        assertThat(repository.findConversationIds()).isEmpty();
    }
}
