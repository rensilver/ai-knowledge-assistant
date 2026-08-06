package com.rensilver.ai_knowledge_assistant.rag;

import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Splits a document's extracted text into token-bounded chunks small enough
 * to embed meaningfully and fit several at once inside the chat model's
 * context window (see {@code OllamaConfig}'s {@code numCtx}).
 */
@Service
public class DocumentChunker {

    private final int chunkSize;

    public DocumentChunker(@Value("${app.rag.chunk-size:800}") int chunkSize) {
        this.chunkSize = chunkSize;
    }

    /**
     * @param documentId id of the owning {@code DocumentEntity}, stored in
     *                   each chunk's metadata so {@code VectorStoreService}
     *                   can filter/delete by it later
     * @param filename   original filename, stored in metadata so answers
     *                   can cite their source
     */
    public List<Document> chunk(UUID documentId, String filename, String text) {
        Document source = new Document(text, Map.of(
                "document_id", documentId.toString(),
                "filename", filename
        ));

        TokenTextSplitter splitter = TokenTextSplitter.builder()
                .withChunkSize(chunkSize)
                .build();

        return splitter.split(source);
    }
}
