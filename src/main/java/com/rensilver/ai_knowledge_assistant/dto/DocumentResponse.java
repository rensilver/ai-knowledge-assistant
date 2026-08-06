package com.rensilver.ai_knowledge_assistant.dto;

import com.rensilver.ai_knowledge_assistant.entity.DocumentEntity;

import java.time.Instant;
import java.util.UUID;

/**
 * Returned by {@code POST /documents/upload} and {@code GET /documents}.
 */
public record DocumentResponse(
        UUID id,
        String filename,
        String contentType,
        long sizeBytes,
        String status,
        String uploadedBy,
        Instant createdAt
) {
    public static DocumentResponse from(DocumentEntity entity) {
        return new DocumentResponse(
                entity.getId(),
                entity.getFilename(),
                entity.getContentType(),
                entity.getSizeBytes(),
                entity.getStatus().name(),
                entity.getUploadedBy().getEmail(),
                entity.getCreatedAt()
        );
    }
}
