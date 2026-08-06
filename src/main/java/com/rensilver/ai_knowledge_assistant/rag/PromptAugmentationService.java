package com.rensilver.ai_knowledge_assistant.rag;

import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Merges retrieved chunks and the user's question into the single user
 * message sent to the {@code ChatClient}. The system prompt (grounding
 * rules, citation instructions) lives once in {@code OllamaConfig}'s
 * default system message — this only supplies the per-request context.
 */
@Service
public class PromptAugmentationService {

    public String augment(String question, List<Document> chunks) {
        if (chunks.isEmpty()) {
            return """
                    No relevant context was found in the company knowledge base for the
                    question below. Say so explicitly instead of answering from general
                    knowledge.

                    Question: %s
                    """.formatted(question);
        }

        StringBuilder context = new StringBuilder();
        for (int i = 0; i < chunks.size(); i++) {
            Document chunk = chunks.get(i);
            Object filename = chunk.getMetadata().getOrDefault("filename", "unknown source");
            context.append("[%d] Source: %s%n%s%n%n".formatted(i + 1, filename, chunk.getText()));
        }

        return """
                Context from company documents:

                %s
                Question: %s

                Answer using only the context above and cite the source document name(s) you used.
                """.formatted(context, question);
    }
}
