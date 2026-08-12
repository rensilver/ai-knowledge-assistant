package com.rensilver.ai_knowledge_assistant.agent;

import com.rensilver.ai_knowledge_assistant.chat.ConversationContext;
import com.rensilver.ai_knowledge_assistant.chat.ConversationMemoryService;
import com.rensilver.ai_knowledge_assistant.dto.ChatRequest;
import com.rensilver.ai_knowledge_assistant.dto.ChatResponse;
import com.rensilver.ai_knowledge_assistant.entity.UserEntity;
import com.rensilver.ai_knowledge_assistant.repository.UserRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Handles {@code POST /agent}: the V4, tool-calling counterpart to
 * {@code ChatService}.
 *
 * <p>{@code ChatService} runs a fixed retrieve-then-answer pipeline. Here the
 * model drives instead — it decides which of {@link KnowledgeBaseTools} to
 * call, with what arguments, and how many times, before answering. That suits
 * questions a single retrieval can't serve ("compare what the security policy
 * and the onboarding guide say about laptops", "how many documents mention
 * pgvector?"), at the cost of extra model round-trips per turn.
 */
@Service
public class AgentService {

    private final ChatClient agentChatClient;
    private final UserRepository userRepository;
    private final ConversationMemoryService conversationMemoryService;
    private final MeterRegistry meterRegistry;

    public AgentService(
            @Qualifier("agentChatClient") ChatClient agentChatClient,
            UserRepository userRepository,
            ConversationMemoryService conversationMemoryService,
            MeterRegistry meterRegistry
    ) {
        this.agentChatClient = agentChatClient;
        this.userRepository = userRepository;
        this.conversationMemoryService = conversationMemoryService;
        this.meterRegistry = meterRegistry;
    }

    public ChatResponse chat(ChatRequest request, String userEmail) {
        UserEntity user = userRepository.getByEmail(userEmail);

        ConversationContext context = conversationMemoryService.resolve(request.conversationId(), user.getId());

        // Includes however many tool round-trips the model makes, unlike
        // rag.answer.duration (RagService), which is always a single call.
        String answer = Timer.builder("agent.turn.duration")
                .register(meterRegistry)
                .record(() -> agentChatClient.prompt()
                        .user(request.message())
                        .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, context.memoryKey()))
                        .call()
                        .content());

        // Sources aren't reported here the way ChatService does: the model may
        // call searchDocuments any number of times (or not at all), so there is
        // no single retrieval to attribute the answer to. The tool feeds it
        // "name (page N)" labels and the system prompt requires citing them
        // inline instead.
        return new ChatResponse(answer, context.conversationId(), List.of());
    }
}
