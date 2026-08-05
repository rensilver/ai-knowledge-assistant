package com.rensilver.ai_knowledge_assistant.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Central configuration for Ollama-backed AI models used across the assistant.
 *
 * <p>Spring AI's auto-configuration (via {@code spring-ai-starter-model-ollama})
 * already creates {@link OllamaChatModel} and {@link OllamaEmbeddingModel} beans
 * from {@code application.yml} properties (spring.ai.ollama.*). This class exists to:
 *
 * <ul>
 *   <li>Explicitly pin the chat model (llama3.2) and embedding model (nomic-embed-text),
 *       since they differ and rely on separate property namespaces;</li>
 *   <li>Set sane default {@link OllamaOptions} (temperature, context window) for the
 *       RAG use case, where we want low-temperature, grounded answers;</li>
 *   <li>Expose a pre-configured {@link ChatClient} bean (with conversation memory)
 *       so services like {@code ChatService} and {@code RagService} don't have to
 *       rebuild it themselves.</li>
 * </ul>
 */
@Configuration
public class OllamaConfig {

    @Value("${spring.ai.ollama.chat.options.model:llama3.2}")
    private String chatModel;

    @Value("${spring.ai.ollama.chat.options.temperature:0.3}")
    private Double temperature;

    /**
     * Default options applied to every chat completion request.
     * Low temperature favors deterministic, fact-grounded RAG answers
     * over creative/varied output.
     */
    @Bean
    public OllamaOptions ollamaChatOptions() {
        return OllamaOptions.builder()
                .model(chatModel)
                .temperature(temperature)
                .topP(0.9)
                .numCtx(4096)
                .build();
    }

    /**
     * Raw storage for conversation messages. In-memory for now (backed by a
     * ConcurrentHashMap) — swap for {@code JdbcChatMemoryRepository} once V2
     * (persistent conversation history) is implemented, so messages survive
     * restarts and are shared across instances. When you do, back it with the
     * existing {@code chat_history} table rather than letting Spring AI own
     * a second, parallel history table.
     */
    @Bean
    public ChatMemoryRepository chatMemoryRepository() {
        return new InMemoryChatMemoryRepository();
    }

    /**
     * Policy layer on top of the repository: keeps a bounded window of the
     * most recent messages per conversation so prompts don't grow unbounded
     * (and blow past numCtx) as a conversation gets long.
     */
    @Bean
    public ChatMemory chatMemory(ChatMemoryRepository chatMemoryRepository) {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(chatMemoryRepository)
                .maxMessages(20)
                .build();
    }

    /**
     * Pre-wired ChatClient used by ChatService / RagService.
     * Includes a message-window memory advisor so multi-turn conversations
     * retain context without services having to manage history manually.
     *
     * @param chatModel autoconfigured by spring-ai-starter-model-ollama
     * @param chatMemory conversation memory store
     */
    @Bean
    public ChatClient chatClient(ChatModel chatModel, ChatMemory chatMemory) {
        return ChatClient.builder(chatModel)
                .defaultOptions(ollamaChatOptions())
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .defaultSystem("""
                        You are a corporate knowledge assistant. Answer strictly using
                        the retrieved document context provided to you. If the context
                        does not contain the answer, say so explicitly instead of guessing.
                        Always cite the source document name when possible.
                        """)
                .build();
    }
}