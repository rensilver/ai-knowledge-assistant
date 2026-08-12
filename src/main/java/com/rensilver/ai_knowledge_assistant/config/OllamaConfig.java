package com.rensilver.ai_knowledge_assistant.config;

import com.rensilver.ai_knowledge_assistant.agent.ExternalKnowledgeTools;
import com.rensilver.ai_knowledge_assistant.agent.KnowledgeBaseTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

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
 *   <li>Set sane default {@link OllamaChatOptions} (temperature, context window) for the
 *       RAG use case, where we want low-temperature, grounded answers;</li>
 *   <li>Expose a pre-configured {@link ChatClient} bean (with conversation memory)
 *       so services like {@code ChatService} and {@code RagService} don't have to
 *       rebuild it themselves.</li>
 * </ul>
 */
@Configuration
public class OllamaConfig {

    private static final double DEFAULT_TOP_P = 0.9;
    private static final int DEFAULT_NUM_CTX = 4096;

    @Value("${spring.ai.ollama.chat.options.model:llama3.2:latest}")
    private String chatModel;

    @Value("${spring.ai.ollama.chat.options.temperature:0.3}")
    private Double temperature;

    /**
     * Default options applied to every chat completion request.
     * Low temperature favors deterministic, fact-grounded RAG answers
     * over creative/varied output.
     */
    @Bean
    public OllamaChatOptions ollamaChatOptions() {
        return newChatOptionsBuilder().build();
    }

    private OllamaChatOptions.Builder newChatOptionsBuilder() {
        return OllamaChatOptions.builder()
                .model(chatModel)
                .temperature(temperature)
                .topP(DEFAULT_TOP_P)
                .numCtx(DEFAULT_NUM_CTX);
    }

    /**
     * Policy layer on top of the repository: keeps a bounded window of the
     * most recent messages per conversation so prompts don't grow unbounded
     * (and blow past numCtx) as a conversation gets long.
     *
     * @param chatMemoryRepository {@code JdbcChatHistoryRepository} — persists
     *                             the window to {@code chat_history} so
     *                             conversations survive restarts
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
    @Primary
    public ChatClient chatClient(ChatModel chatModel, ChatMemory chatMemory) {
        return ChatClient.builder(chatModel)
                .defaultOptions(newChatOptionsBuilder())
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .defaultSystem("""
                        You are a corporate knowledge assistant. Answer strictly using
                        the retrieved document context provided to you. If the context
                        does not contain the answer, say so explicitly instead of guessing.
                        Always cite the source document name when possible.
                        """)
                .build();
    }

    /**
     * ChatClient for the V4 agent endpoint (see {@code AgentService}): same
     * model and memory, but with {@link KnowledgeBaseTools} and
     * {@link ExternalKnowledgeTools} attached so the model can decide to
     * search the knowledge base — or, failing that, the open web — itself
     * instead of being handed a fixed set of retrieved chunks.
     *
     * <p>Kept as a separate bean rather than adding tools to the primary
     * client, so the plain {@code /chat} path stays a predictable single
     * round-trip with no tool-call latency.
     */
    @Bean
    public ChatClient agentChatClient(
            ChatModel chatModel,
            ChatMemory chatMemory,
            KnowledgeBaseTools knowledgeBaseTools,
            ExternalKnowledgeTools externalKnowledgeTools
    ) {
        return ChatClient.builder(chatModel)
                .defaultOptions(newChatOptionsBuilder())
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .defaultTools(knowledgeBaseTools, externalKnowledgeTools)
                .defaultSystem("""
                        You are a corporate knowledge assistant with access to the
                        company's document knowledge base through tools.

                        Use the searchDocuments tool to answer any question about what
                        company documents say — search more than once with different
                        wording if the first result is unhelpful. Use listDocuments for
                        questions about which documents exist rather than their contents.
                        Answer company-related questions only from what these two tools
                        return. If they return nothing relevant, say the knowledge base
                        does not cover it rather than guessing. Cite the document name
                        and page exactly as the tool labels them.

                        For general-knowledge questions unrelated to company documents,
                        or when searchDocuments genuinely found nothing relevant, you may
                        use searchExternalKnowledge (Wikipedia) instead. Always tell the
                        user clearly when an answer came from Wikipedia rather than the
                        company's knowledge base.
                        """)
                .build();
    }
}