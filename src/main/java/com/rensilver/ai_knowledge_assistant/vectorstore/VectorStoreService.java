package com.rensilver.ai_knowledge_assistant.vectorstore;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Thin seam over Spring AI's {@link VectorStore} (backed by
 * {@code PgVectorStore}, see {@code PgVectorConfig}) so callers depend on
 * this rather than the Spring AI API directly — keeps the vector store
 * implementation swappable and centralizes the {@code document_id} metadata
 * filter used to scope searches/deletes to a single document.
 */
@Service
public class VectorStoreService {

    private final VectorStore vectorStore;

    public VectorStoreService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    public void index(List<Document> chunks) {
        vectorStore.add(chunks);
    }

    public List<Document> search(String query, int topK, double similarityThreshold) {
        SearchRequest request = SearchRequest.builder()
                .query(query)
                .topK(topK)
                .similarityThreshold(similarityThreshold)
                .build();
        return vectorStore.similaritySearch(request);
    }

    public void deleteByDocumentId(UUID documentId) {
        var filter = new FilterExpressionBuilder()
                .eq("document_id", documentId.toString())
                .build();
        vectorStore.delete(filter);
    }
}
