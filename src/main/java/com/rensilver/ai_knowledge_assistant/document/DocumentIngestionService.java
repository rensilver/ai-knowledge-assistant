package com.rensilver.ai_knowledge_assistant.document;

import com.rensilver.ai_knowledge_assistant.entity.DocumentStatus;
import com.rensilver.ai_knowledge_assistant.rag.DocumentChunker;
import com.rensilver.ai_knowledge_assistant.repository.DocumentRepository;
import com.rensilver.ai_knowledge_assistant.vectorstore.VectorStoreService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.ai.document.Document;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Runs the parse -&gt; chunk -&gt; embed -&gt; index part of ingestion off the
 * request thread, so {@code DocumentService.upload} can return as soon as the
 * file is stored instead of blocking the HTTP call for the whole pipeline.
 *
 * <p>Because this runs on a background thread, nothing downstream of it can
 * report a failure to the original caller — unlike the synchronous version
 * this replaced, every exception here (not just {@code DocumentProcessingException})
 * is caught so the document is reliably left in a terminal
 * {@link DocumentStatus#COMPLETED}/{@link DocumentStatus#FAILED} state rather
 * than stuck in {@code PROCESSING} forever with the error only visible in logs.
 */
@Service
public class DocumentIngestionService {

    private static final Logger log = LoggerFactory.getLogger(DocumentIngestionService.class);

    private final DocumentRepository documentRepository;
    private final PdfParserService pdfParserService;
    private final DocumentChunker documentChunker;
    private final VectorStoreService vectorStoreService;
    private final MeterRegistry meterRegistry;

    public DocumentIngestionService(
            DocumentRepository documentRepository,
            PdfParserService pdfParserService,
            DocumentChunker documentChunker,
            VectorStoreService vectorStoreService,
            MeterRegistry meterRegistry
    ) {
        this.documentRepository = documentRepository;
        this.pdfParserService = pdfParserService;
        this.documentChunker = documentChunker;
        this.vectorStoreService = vectorStoreService;
        this.meterRegistry = meterRegistry;
    }

    @Async("documentIngestionExecutor")
    public void ingest(UUID documentId, String filename, byte[] bytes) {
        MDC.put("documentId", documentId.toString());
        Timer.Sample sample = Timer.start(meterRegistry);
        String outcome = "failed";

        try {
            List<PdfPage> pages = pdfParserService.extractPages(bytes);
            List<Document> chunks = documentChunker.chunk(documentId, filename, pages);
            vectorStoreService.index(chunks);

            updateStatus(documentId, DocumentStatus.COMPLETED);
            outcome = "completed";
            log.info("Ingested document {} ({} pages, {} chunks)", documentId, pages.size(), chunks.size());
        } catch (Exception e) {
            log.error("Ingestion failed for document {}", documentId, e);
            updateStatus(documentId, DocumentStatus.FAILED);
        } finally {
            sample.stop(Timer.builder("document.ingestion.duration")
                    .tag("outcome", outcome)
                    .register(meterRegistry));
            MDC.remove("documentId");
        }
    }

    @Transactional
    void updateStatus(UUID documentId, DocumentStatus status) {
        documentRepository.findById(documentId).ifPresent(entity -> {
            entity.setStatus(status);
            documentRepository.save(entity);
        });
    }
}
