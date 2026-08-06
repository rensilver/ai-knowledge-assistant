package com.rensilver.ai_knowledge_assistant.rag;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Orchestrates one grounded chat turn: retrieve relevant chunks, fold them
 * into the prompt, and let the {@code ChatClient} (pre-wired in
 * {@code OllamaConfig} with conversation memory) generate the answer.
 */
@Service
public class RagService {

    private final ChatClient chatClient;
    private final ContextRetriever contextRetriever;
    private final PromptAugmentationService promptAugmentationService;

    public RagService(
            ChatClient chatClient,
            ContextRetriever contextRetriever,
            PromptAugmentationService promptAugmentationService
    ) {
        this.chatClient = chatClient;
        this.contextRetriever = contextRetriever;
        this.promptAugmentationService = promptAugmentationService;
    }

    public RagAnswer answer(String question, String conversationId) {
        List<Document> chunks = contextRetriever.retrieve(question);
        String augmentedPrompt = promptAugmentationService.augment(question, chunks);

        String content = chatClient.prompt()
                .user(augmentedPrompt)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .call()
                .content();

        List<String> sources = chunks.stream()
                .map(chunk -> String.valueOf(chunk.getMetadata().getOrDefault("filename", "unknown source")))
                .distinct()
                .toList();

        return new RagAnswer(content, sources);
    }
}
