-- Backs JdbcChatHistoryRepository (our ChatMemoryRepository implementation),
-- replacing the in-memory store so conversations survive restarts.
--
-- Deliberately NOT Spring AI's shipped SPRING_AI_CHAT_MEMORY schema: that one
-- keys rows by a single `conversation_id VARCHAR(36)`, which cannot hold this
-- app's user-namespaced memory key ("<userId>:<conversationId>" = 73 chars).
-- Splitting it into two real UUID columns also lets a user's conversations be
-- listed and cascades cleanly when a user is deleted.
CREATE TABLE IF NOT EXISTS chat_history (
    id              BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    conversation_id UUID        NOT NULL,
    user_id         UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    message_type    VARCHAR(10) NOT NULL CHECK (message_type IN ('USER', 'ASSISTANT', 'SYSTEM', 'TOOL')),
    content         TEXT        NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Every read is "all messages for this (user, conversation), in write order",
-- and `id` doubles as that ordering key.
CREATE INDEX IF NOT EXISTS idx_chat_history_conversation
    ON chat_history (user_id, conversation_id, id);
