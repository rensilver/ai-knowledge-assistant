package com.rensilver.ai_knowledge_assistant.chat;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConversationKeyTest {

    @Test
    void roundTripsThroughItsPackedForm() {
        ConversationKey key = new ConversationKey(UUID.randomUUID(), UUID.randomUUID());

        assertThat(ConversationKey.parse(key.format())).isEqualTo(key);
    }

    @Test
    void keepsUsersApartForTheSameConversationId() {
        UUID conversationId = UUID.randomUUID();

        String first = new ConversationKey(UUID.randomUUID(), conversationId).format();
        String second = new ConversationKey(UUID.randomUUID(), conversationId).format();

        // The whole point of namespacing: guessing another user's conversation
        // id must not be enough to address their history.
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void rejectsKeysThatAreNotUserNamespaced() {
        assertThatThrownBy(() -> ConversationKey.parse(UUID.randomUUID().toString()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Malformed conversation key");
    }

    @Test
    void rejectsNonUuidHalves() {
        assertThatThrownBy(() -> ConversationKey.parse("not-a-uuid:" + UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Malformed conversation key");
    }
}
