package com.rensilver.ai_knowledge_assistant.document;

import com.rensilver.ai_knowledge_assistant.dto.DocumentResponse;
import com.rensilver.ai_knowledge_assistant.entity.DocumentEntity;
import com.rensilver.ai_knowledge_assistant.entity.DocumentStatus;
import com.rensilver.ai_knowledge_assistant.entity.UserEntity;
import com.rensilver.ai_knowledge_assistant.exception.DocumentNotFoundException;
import com.rensilver.ai_knowledge_assistant.exception.DocumentProcessingException;
import com.rensilver.ai_knowledge_assistant.exception.UnsupportedFileTypeException;
import com.rensilver.ai_knowledge_assistant.rag.DocumentChunker;
import com.rensilver.ai_knowledge_assistant.repository.DocumentRepository;
import com.rensilver.ai_knowledge_assistant.repository.UserRepository;
import com.rensilver.ai_knowledge_assistant.vectorstore.VectorStoreService;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.UUID;

/**
 * Orchestrates the full V1 ingestion pipeline for one uploaded PDF:
 * store the raw file -> extract text -> chunk -> embed &amp; index into
 * pgvector -> mark the {@code documents} row COMPLETED (or FAILED).
 *
 * <p>Runs synchronously within the upload request — acceptable for the MVP
 * (no job queue yet); a slow/large PDF simply makes the upload call take
 * longer.
 */
@Service
public class DocumentService {

    private static final String PDF_CONTENT_TYPE = "application/pdf";

    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;
    private final DocumentStorageService storageService;
    private final PdfParserService pdfParserService;
    private final DocumentChunker documentChunker;
    private final VectorStoreService vectorStoreService;

    public DocumentService(
            DocumentRepository documentRepository,
            UserRepository userRepository,
            DocumentStorageService storageService,
            PdfParserService pdfParserService,
            DocumentChunker documentChunker,
            VectorStoreService vectorStoreService
    ) {
        this.documentRepository = documentRepository;
        this.userRepository = userRepository;
        this.storageService = storageService;
        this.pdfParserService = pdfParserService;
        this.documentChunker = documentChunker;
        this.vectorStoreService = vectorStoreService;
    }

    public DocumentResponse upload(MultipartFile file, String uploaderEmail) {
        if (!PDF_CONTENT_TYPE.equals(file.getContentType())) {
            throw new UnsupportedFileTypeException(file.getContentType());
        }

        UserEntity uploader = userRepository.findByEmail(uploaderEmail)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + uploaderEmail));

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read uploaded file", e);
        }

        DocumentEntity entity = DocumentEntity.builder()
                .filename(file.getOriginalFilename())
                .contentType(file.getContentType())
                .sizeBytes(file.getSize())
                .status(DocumentStatus.PROCESSING)
                .uploadedBy(uploader)
                .build();
        entity = documentRepository.save(entity);

        storageService.store(entity.getId(), bytes);

        try {
            String text = pdfParserService.extractText(bytes);
            List<Document> chunks = documentChunker.chunk(entity.getId(), entity.getFilename(), text);
            vectorStoreService.index(chunks);

            entity.setStatus(DocumentStatus.COMPLETED);
            entity = documentRepository.save(entity);
        } catch (DocumentProcessingException e) {
            entity.setStatus(DocumentStatus.FAILED);
            documentRepository.save(entity);
            throw e;
        }

        return DocumentResponse.from(entity);
    }

    @Transactional(readOnly = true)
    public List<DocumentResponse> list() {
        return documentRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(DocumentResponse::from)
                .toList();
    }

    @Transactional
    public void delete(UUID id) {
        DocumentEntity entity = documentRepository.findById(id)
                .orElseThrow(() -> new DocumentNotFoundException(id));

        vectorStoreService.deleteByDocumentId(id);
        storageService.delete(id);
        documentRepository.delete(entity);
    }
}
