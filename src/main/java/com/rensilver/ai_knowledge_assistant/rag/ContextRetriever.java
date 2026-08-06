package com.rensilver.ai_knowledge_assistant.rag;

import com.rensilver.ai_knowledge_assistant.vectorstore.VectorStoreService;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Fetches the document chunks most relevant to a user's question, ahead of
 * {@code PromptAugmentationService} folding them into the prompt sent to
 * the LLM.
 */
@Service
public class ContextRetriever {

    private final VectorStoreService vectorStoreService;
    private final int topK;
    private final double similarityThreshold;

    public ContextRetriever(
            VectorStoreService vectorStoreService,
            @Value("${app.rag.top-k:5}") int topK,
            @Value("${app.rag.similarity-threshold:0.5}") double similarityThreshold
    ) {
        this.vectorStoreService = vectorStoreService;
        this.topK = topK;
        this.similarityThreshold = similarityThreshold;
    }

    public List<Document> retrieve(String query) {
        return vectorStoreService.search(query, topK, similarityThreshold);
    }
}
