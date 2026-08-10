package com.rensilver.ai_knowledge_assistant.dto;

import com.rensilver.ai_knowledge_assistant.rag.DocumentChunker;
import org.springframework.ai.document.Document;

/**
 * One document location an answer was grounded in, e.g.
 * {@code AWS Architecture Guide.pdf}, page 12.
 *
 * @param filename original name of the uploaded document
 * @param page     1-based page the cited chunk came from, or {@code null} for
 *                 documents indexed before page tracking existed
 */
public record SourceReference(String filename, Integer page) {

    private static final String UNKNOWN_FILENAME = "unknown source";

    public static SourceReference from(Document chunk) {
        Object filename = chunk.getMetadata().get(DocumentChunker.FILENAME);
        return new SourceReference(
                filename != null ? filename.toString() : UNKNOWN_FILENAME,
                readPage(chunk)
        );
    }

    /**
     * pgvector round-trips metadata through JSON, so a page stored as an
     * {@code Integer} can come back as any {@link Number} subtype.
     */
    private static Integer readPage(Document chunk) {
        Object page = chunk.getMetadata().get(DocumentChunker.PAGE);
        return page instanceof Number number ? number.intValue() : null;
    }

    /** Rendered into prompts and readable by a human: {@code guide.pdf (page 12)}. */
    public String describe() {
        return page != null ? "%s (page %d)".formatted(filename, page) : filename;
    }
}
