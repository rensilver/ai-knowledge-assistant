package com.rensilver.ai_knowledge_assistant.document;

import com.rensilver.ai_knowledge_assistant.entity.DocumentEntity;
import com.rensilver.ai_knowledge_assistant.entity.DocumentStatus;
import com.rensilver.ai_knowledge_assistant.exception.DocumentProcessingException;
import com.rensilver.ai_knowledge_assistant.rag.DocumentChunker;
import com.rensilver.ai_knowledge_assistant.repository.DocumentRepository;
import com.rensilver.ai_knowledge_assistant.vectorstore.VectorStoreService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Exercises {@link DocumentIngestionService#ingest} directly (bypassing the
 * {@code @Async} proxy — the annotation only changes which thread runs the
 * method, not its logic) to pin down the COMPLETED/FAILED branches that used
 * to live inline in {@code DocumentService.upload} before ingestion moved
 * off the request thread.
 */
@ExtendWith(MockitoExtension.class)
class DocumentIngestionServiceTest {

    @Mock
    private DocumentRepository documentRepository;
    @Mock
    private PdfParserService pdfParserService;
    @Mock
    private DocumentChunker documentChunker;
    @Mock
    private VectorStoreService vectorStoreService;

    private DocumentIngestionService ingestionService;

    @BeforeEach
    void setUp() {
        ingestionService = new DocumentIngestionService(
                documentRepository, pdfParserService, documentChunker, vectorStoreService,
                new SimpleMeterRegistry());
    }

    @Test
    void marksTheDocumentCompletedOnSuccessfulIngestion() {
        UUID documentId = UUID.randomUUID();
        DocumentEntity entity = entity(documentId, DocumentStatus.PROCESSING);
        byte[] bytes = "%PDF-1.4".getBytes();

        List<PdfPage> pages = List.of(new PdfPage(1, "Some text"));
        List<Document> chunks = List.of(new Document("Some text"));
        when(pdfParserService.extractPages(bytes)).thenReturn(pages);
        when(documentChunker.chunk(documentId, "policy.pdf", pages)).thenReturn(chunks);
        when(documentRepository.findById(documentId)).thenReturn(Optional.of(entity));

        ingestionService.ingest(documentId, "policy.pdf", bytes);

        verify(vectorStoreService).index(chunks);

        ArgumentCaptor<DocumentEntity> savedCaptor = ArgumentCaptor.forClass(DocumentEntity.class);
        verify(documentRepository).save(savedCaptor.capture());
        assertThat(savedCaptor.getValue().getStatus()).isEqualTo(DocumentStatus.COMPLETED);
    }

    @Test
    void marksTheDocumentFailedWhenExtractionThrows() {
        UUID documentId = UUID.randomUUID();
        DocumentEntity entity = entity(documentId, DocumentStatus.PROCESSING);
        byte[] bytes = "not actually a pdf".getBytes();

        when(pdfParserService.extractPages(bytes))
                .thenThrow(new DocumentProcessingException("Failed to extract text from PDF", new RuntimeException()));
        when(documentRepository.findById(documentId)).thenReturn(Optional.of(entity));

        ingestionService.ingest(documentId, "corrupt.pdf", bytes);

        verifyNoInteractions(vectorStoreService);

        ArgumentCaptor<DocumentEntity> savedCaptor = ArgumentCaptor.forClass(DocumentEntity.class);
        verify(documentRepository).save(savedCaptor.capture());
        assertThat(savedCaptor.getValue().getStatus()).isEqualTo(DocumentStatus.FAILED);
    }

    @Test
    void marksTheDocumentFailedWhenIndexingThrowsAnUncheckedException() {
        UUID documentId = UUID.randomUUID();
        DocumentEntity entity = entity(documentId, DocumentStatus.PROCESSING);
        byte[] bytes = "%PDF-1.4".getBytes();
        List<PdfPage> pages = List.of(new PdfPage(1, "text"));
        List<Document> chunks = List.of(new Document("text"));

        when(pdfParserService.extractPages(bytes)).thenReturn(pages);
        when(documentChunker.chunk(documentId, "policy.pdf", pages)).thenReturn(chunks);
        // Not a DocumentProcessingException: e.g. the vector store connection
        // drops mid-index. Ingestion runs on a background thread with nothing
        // to catch this upstream, so the service itself must not let it escape
        // uncaught and leave the row stuck at PROCESSING.
        doThrow(new RuntimeException("pgvector unreachable")).when(vectorStoreService).index(chunks);
        when(documentRepository.findById(documentId)).thenReturn(Optional.of(entity));

        ingestionService.ingest(documentId, "policy.pdf", bytes);

        ArgumentCaptor<DocumentEntity> savedCaptor = ArgumentCaptor.forClass(DocumentEntity.class);
        verify(documentRepository).save(savedCaptor.capture());
        assertThat(savedCaptor.getValue().getStatus()).isEqualTo(DocumentStatus.FAILED);
    }

    @Test
    void toleratesTheDocumentRowHavingBeenDeletedBeforeIngestionFinished() {
        UUID documentId = UUID.randomUUID();
        byte[] bytes = "%PDF-1.4".getBytes();
        List<PdfPage> pages = List.of(new PdfPage(1, "text"));
        List<Document> chunks = List.of(new Document("text"));

        when(pdfParserService.extractPages(bytes)).thenReturn(pages);
        when(documentChunker.chunk(documentId, "policy.pdf", pages)).thenReturn(chunks);
        when(documentRepository.findById(documentId)).thenReturn(Optional.empty());

        ingestionService.ingest(documentId, "policy.pdf", bytes);

        verify(documentRepository, never()).save(any());
    }

    private DocumentEntity entity(UUID id, DocumentStatus status) {
        return DocumentEntity.builder()
                .id(id)
                .filename("policy.pdf")
                .contentType("application/pdf")
                .sizeBytes(100)
                .status(status)
                .build();
    }
}
