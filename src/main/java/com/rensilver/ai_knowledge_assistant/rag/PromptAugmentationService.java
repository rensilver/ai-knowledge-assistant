package com.rensilver.ai_knowledge_assistant.rag;

import com.rensilver.ai_knowledge_assistant.dto.SourceReference;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Builds the system message for one grounded chat turn: the standing grounding
 * rules plus the chunks retrieved for this question.
 *
 * <p>The retrieved context deliberately goes in the <em>system</em> message
 * rather than being concatenated onto the user's question.
 * {@code MessageChatMemoryAdvisor} persists only the last user message, so
 * keeping the question raw means conversation memory stores just the question
 * and the answer — the (much larger) context isn't copied into history on every
 * turn, and the bounded message window holds real turns instead of stale
 * context.
 */
@Service
public class PromptAugmentationService {

    private static final String GROUNDING_RULES = """
            You are a corporate knowledge assistant. Answer strictly using the
            document context provided below. If the context does not contain the
            answer, say so explicitly instead of guessing or falling back on
            general knowledge.

            Cite the source of every claim as the document name followed by its
            page, exactly as labelled in the context (for example:
            "According to 'AWS Architecture Guide.pdf' (page 12), ...").
            """;

    public String systemPrompt(List<Document> chunks) {
        if (chunks.isEmpty()) {
            return GROUNDING_RULES + """

                    No relevant context was found in the company knowledge base for
                    this question. Tell the user that the knowledge base does not
                    cover it, and do not answer from general knowledge.
                    """;
        }

        StringBuilder context = new StringBuilder(GROUNDING_RULES);
        context.append("\nContext from company documents:\n\n");

        for (int i = 0; i < chunks.size(); i++) {
            Document chunk = chunks.get(i);
            context.append("[%d] Source: %s%n%s%n%n".formatted(
                    i + 1,
                    SourceReference.from(chunk).describe(),
                    chunk.getText()
            ));
        }

        return context.toString();
    }
}
