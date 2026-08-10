package com.rensilver.ai_knowledge_assistant.rag;

import com.rensilver.ai_knowledge_assistant.document.PdfPage;
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

    /** Metadata keys carried on every chunk into the vector store. */
    public static final String DOCUMENT_ID = "document_id";
    public static final String FILENAME = "filename";
    public static final String PAGE = "page";

    private final int chunkSize;

    public DocumentChunker(@Value("${app.rag.chunk-size:800}") int chunkSize) {
        this.chunkSize = chunkSize;
    }

    /**
     * Chunks each page independently so a chunk never straddles a page
     * boundary and can therefore be attributed to exactly one page. The cost
     * is slightly undersized chunks at the end of each page, which is a good
     * trade for citations that point at a specific page.
     *
     * @param documentId id of the owning {@code DocumentEntity}, stored in
     *                   each chunk's metadata so {@code VectorStoreService}
     *                   can filter/delete by it later
     * @param filename   original filename, stored in metadata so answers
     *                   can cite their source
     */
    public List<Document> chunk(UUID documentId, String filename, List<PdfPage> pages) {
        TokenTextSplitter splitter = TokenTextSplitter.builder()
                .withChunkSize(chunkSize)
                .build();

        return pages.stream()
                .map(page -> new Document(page.text(), Map.of(
                        DOCUMENT_ID, documentId.toString(),
                        FILENAME, filename,
                        PAGE, page.number()
                )))
                // split() copies each source document's metadata onto every
                // chunk it produces, so page/filename survive the split.
                .flatMap(pageDocument -> splitter.split(pageDocument).stream())
                .toList();
    }
}
