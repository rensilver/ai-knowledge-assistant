package com.rensilver.ai_knowledge_assistant.document;

import com.rensilver.ai_knowledge_assistant.dto.DocumentResponse;
import com.rensilver.ai_knowledge_assistant.entity.DocumentEntity;
import com.rensilver.ai_knowledge_assistant.entity.DocumentStatus;
import com.rensilver.ai_knowledge_assistant.entity.UserEntity;
import com.rensilver.ai_knowledge_assistant.exception.DocumentNotFoundException;
import com.rensilver.ai_knowledge_assistant.exception.UnsupportedFileTypeException;
import com.rensilver.ai_knowledge_assistant.repository.DocumentRepository;
import com.rensilver.ai_knowledge_assistant.repository.UserRepository;
import com.rensilver.ai_knowledge_assistant.vectorstore.VectorStoreService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.UUID;

/**
 * Orchestrates the V1 ingestion pipeline for one uploaded PDF: store the raw
 * file, create the {@code documents} row as {@code PROCESSING}, and hand the
 * parse -> chunk -> embed &amp; index work off to {@link DocumentIngestionService}
 * to run asynchronously — the upload call returns as soon as the file is
 * safely stored, rather than blocking for the whole pipeline.
 */
@Service
public class DocumentService {

    private static final String PDF_CONTENT_TYPE = "application/pdf";

    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;
    private final DocumentStorageService storageService;
    private final DocumentIngestionService documentIngestionService;
    private final VectorStoreService vectorStoreService;

    public DocumentService(
            DocumentRepository documentRepository,
            UserRepository userRepository,
            DocumentStorageService storageService,
            DocumentIngestionService documentIngestionService,
            VectorStoreService vectorStoreService
    ) {
        this.documentRepository = documentRepository;
        this.userRepository = userRepository;
        this.storageService = storageService;
        this.documentIngestionService = documentIngestionService;
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
        documentIngestionService.ingest(entity.getId(), entity.getFilename(), bytes);

        return DocumentResponse.from(entity);
    }

    @Transactional(readOnly = true)
    public DocumentResponse get(UUID id) {
        return documentRepository.findById(id)
                .map(DocumentResponse::from)
                .orElseThrow(() -> new DocumentNotFoundException(id));
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
