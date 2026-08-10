package com.rensilver.ai_knowledge_assistant.rag;

import com.rensilver.ai_knowledge_assistant.dto.SourceReference;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.util.Comparator;
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

        String content = chatClient.prompt()
                // Context goes in the system message, and the user message stays
                // the raw question: MessageChatMemoryAdvisor persists only the
                // last user message, so history records what was asked rather
                // than a copy of every chunk retrieved for it.
                .system(promptAugmentationService.systemPrompt(chunks))
                .user(question)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .call()
                .content();

        return new RagAnswer(content, sourcesOf(chunks));
    }

    /**
     * One entry per cited document page, ordered as a reader would expect
     * rather than by retrieval rank.
     */
    private List<SourceReference> sourcesOf(List<Document> chunks) {
        return chunks.stream()
                .map(SourceReference::from)
                .distinct()
                .sorted(Comparator.comparing(SourceReference::filename)
                        .thenComparing(SourceReference::page, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }
}
