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
 *
 * <p>Retrieval deliberately over-fetches and then re-ranks: vector search alone
 * decides the final set purely on embedding proximity, so anything it ranks
 * just outside {@code topK} is lost for good. Pulling a wider candidate set and
 * letting {@link ReRanker} choose the final {@code topK} gives that second
 * signal something to work with, at the cost of one larger index scan.
 */
@Service
public class ContextRetriever {

    private final VectorStoreService vectorStoreService;
    private final ReRanker reRanker;
    private final int topK;
    private final double similarityThreshold;
    private final int overfetchFactor;

    public ContextRetriever(
            VectorStoreService vectorStoreService,
            ReRanker reRanker,
            @Value("${app.rag.top-k:5}") int topK,
            @Value("${app.rag.similarity-threshold:0.5}") double similarityThreshold,
            @Value("${app.rag.overfetch-factor:4}") int overfetchFactor
    ) {
        this.vectorStoreService = vectorStoreService;
        this.reRanker = reRanker;
        this.topK = topK;
        this.similarityThreshold = similarityThreshold;
        this.overfetchFactor = overfetchFactor;
    }

    public List<Document> retrieve(String query) {
        List<Document> candidates = vectorStoreService.search(query, topK * overfetchFactor, similarityThreshold);
        return reRanker.rerank(query, candidates, topK);
    }
}
