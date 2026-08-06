package com.rensilver.ai_knowledge_assistant.document;

import com.rensilver.ai_knowledge_assistant.exception.DocumentProcessingException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Persists the raw bytes of an uploaded PDF to local disk under
 * {@code app.document.upload-dir}, keyed by the document's id so the
 * original file can be re-processed later without asking the user to
 * re-upload it. Not on the RAG query path — only touched at upload/delete
 * time.
 */
@Service
public class DocumentStorageService {

    private final Path uploadDir;

    public DocumentStorageService(@Value("${app.document.upload-dir:./uploads}") String uploadDir) {
        this.uploadDir = Path.of(uploadDir);
        try {
            Files.createDirectories(this.uploadDir);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not create upload directory: " + this.uploadDir, e);
        }
    }

    public Path store(UUID documentId, byte[] content) {
        Path target = pathFor(documentId);
        try {
            Files.write(target, content);
            return target;
        } catch (IOException e) {
            throw new DocumentProcessingException("Failed to store uploaded file", e);
        }
    }

    public void delete(UUID documentId) {
        try {
            Files.deleteIfExists(pathFor(documentId));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to delete stored file for document " + documentId, e);
        }
    }

    private Path pathFor(UUID documentId) {
        return uploadDir.resolve(documentId + ".pdf");
    }
}
