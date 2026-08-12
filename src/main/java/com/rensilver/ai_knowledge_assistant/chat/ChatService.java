package com.rensilver.ai_knowledge_assistant.chat;

import com.rensilver.ai_knowledge_assistant.dto.ChatRequest;
import com.rensilver.ai_knowledge_assistant.dto.ChatResponse;
import com.rensilver.ai_knowledge_assistant.entity.UserEntity;
import com.rensilver.ai_knowledge_assistant.rag.RagAnswer;
import com.rensilver.ai_knowledge_assistant.rag.RagService;
import com.rensilver.ai_knowledge_assistant.repository.UserRepository;
import org.springframework.stereotype.Service;

/**
 * Handles the {@code POST /chat} use case: resolve who's asking and which
 * conversation this belongs to, then delegate answering to {@link RagService}.
 */
@Service
public class ChatService {

    private final UserRepository userRepository;
    private final ConversationMemoryService conversationMemoryService;
    private final RagService ragService;

    public ChatService(
            UserRepository userRepository,
            ConversationMemoryService conversationMemoryService,
            RagService ragService
    ) {
        this.userRepository = userRepository;
        this.conversationMemoryService = conversationMemoryService;
        this.ragService = ragService;
    }

    public ChatResponse chat(ChatRequest request, String userEmail) {
        UserEntity user = userRepository.getByEmail(userEmail);

        ConversationContext context = conversationMemoryService.resolve(request.conversationId(), user.getId());
        RagAnswer answer = ragService.answer(request.message(), context.memoryKey());

        return new ChatResponse(answer.content(), context.conversationId(), answer.sources());
    }
}
