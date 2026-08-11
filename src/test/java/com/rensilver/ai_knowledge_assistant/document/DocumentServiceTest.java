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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Covers the branches noted as untested in CLAUDE.md's next steps: the
 * unsupported-content-type rejection, the missing-uploader guard, and (now
 * that ingestion is asynchronous — see {@link DocumentIngestionService})
 * that upload() hands off to it rather than doing the parse/chunk/index work
 * itself.
 */
@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {

    @Mock
    private DocumentRepository documentRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private DocumentStorageService storageService;
    @Mock
    private DocumentIngestionService documentIngestionService;
    @Mock
    private VectorStoreService vectorStoreService;

    private DocumentService documentService;

    private final UserEntity uploader = UserEntity.builder()
            .id(UUID.randomUUID())
            .name("Ada Lovelace")
            .email("ada@example.com")
            .build();

    private void createService() {
        documentService = new DocumentService(
                documentRepository, userRepository, storageService, documentIngestionService, vectorStoreService);
    }

    @Test
    void rejectsNonPdfUploadsBeforeTouchingAnyDependency() {
        createService();
        MockMultipartFile file = new MockMultipartFile("file", "notes.txt", "text/plain", "hello".getBytes());

        assertThatThrownBy(() -> documentService.upload(file, "ada@example.com"))
                .isInstanceOf(UnsupportedFileTypeException.class);

        verifyNoInteractions(documentRepository, storageService, documentIngestionService);
    }

    @Test
    void rejectsUploadFromAnUnknownUploader() {
        createService();
        when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());
        MockMultipartFile file = new MockMultipartFile("file", "doc.pdf", "application/pdf", "%PDF-1.4".getBytes());

        assertThatThrownBy(() -> documentService.upload(file, "ghost@example.com"))
                .isInstanceOf(IllegalStateException.class);

        verifyNoInteractions(documentIngestionService);
    }

    @Test
    void storesTheFileAndHandsOffAsyncIngestionThenReturnsProcessing() {
        createService();
        when(userRepository.findByEmail("ada@example.com")).thenReturn(Optional.of(uploader));
        when(documentRepository.save(any(DocumentEntity.class))).thenAnswer(invocation -> {
            DocumentEntity entity = invocation.getArgument(0);
            if (entity.getId() == null) {
                entity.setId(UUID.randomUUID());
            }
            return entity;
        });

        byte[] bytes = "%PDF-1.4 fake content".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "policy.pdf", "application/pdf", bytes);

        DocumentResponse response = documentService.upload(file, "ada@example.com");

        assertThat(response.status()).isEqualTo(DocumentStatus.PROCESSING.name());
        assertThat(response.filename()).isEqualTo("policy.pdf");
        assertThat(response.uploadedBy()).isEqualTo("ada@example.com");

        ArgumentCaptor<UUID> idCaptor = ArgumentCaptor.forClass(UUID.class);
        verify(storageService).store(idCaptor.capture(), eq(bytes));
        verify(documentIngestionService).ingest(eq(idCaptor.getValue()), eq("policy.pdf"), eq(bytes));
        // The response returns before ingestion completes — upload() must never
        // itself flip the status to COMPLETED/FAILED.
        verify(documentRepository, times(1)).save(any(DocumentEntity.class));
    }

    @Test
    void getThrowsWhenTheDocumentDoesNotExist() {
        createService();
        UUID id = UUID.randomUUID();
        when(documentRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> documentService.get(id))
                .isInstanceOf(DocumentNotFoundException.class);
    }

    @Test
    void getReturnsTheCurrentStatus() {
        createService();
        DocumentEntity entity = DocumentEntity.builder()
                .id(UUID.randomUUID())
                .filename("handbook.pdf")
                .contentType("application/pdf")
                .sizeBytes(1024)
                .status(DocumentStatus.COMPLETED)
                .uploadedBy(uploader)
                .build();
        when(documentRepository.findById(entity.getId())).thenReturn(Optional.of(entity));

        DocumentResponse response = documentService.get(entity.getId());

        assertThat(response.status()).isEqualTo(DocumentStatus.COMPLETED.name());
    }

    @Test
    void deleteRemovesTheVectorEntriesTheStoredFileAndTheRow() {
        createService();
        DocumentEntity entity = DocumentEntity.builder()
                .id(UUID.randomUUID())
                .filename("old.pdf")
                .contentType("application/pdf")
                .sizeBytes(10)
                .status(DocumentStatus.COMPLETED)
                .uploadedBy(uploader)
                .build();
        when(documentRepository.findById(entity.getId())).thenReturn(Optional.of(entity));

        documentService.delete(entity.getId());

        verify(vectorStoreService).deleteByDocumentId(entity.getId());
        verify(storageService).delete(entity.getId());
        verify(documentRepository).delete(entity);
    }

    @Test
    void listsDocumentsMappedToResponses() {
        createService();
        DocumentEntity entity = DocumentEntity.builder()
                .id(UUID.randomUUID())
                .filename("a.pdf")
                .contentType("application/pdf")
                .sizeBytes(1)
                .status(DocumentStatus.COMPLETED)
                .uploadedBy(uploader)
                .build();
        when(documentRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(entity));

        List<DocumentResponse> responses = documentService.list();

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).filename()).isEqualTo("a.pdf");
    }
}
