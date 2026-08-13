package com.rensilver.ai_knowledge_assistant.dto;

import com.rensilver.ai_knowledge_assistant.entity.UserEntity;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

/**
 * Returned by {@code GET /users} and {@code PATCH /users/{id}/role}.
 */
public record UserResponse(
        UUID id,
        String name,
        String email,
        @Schema(description = "ADMIN or USER")
        String role,
        Instant createdAt
) {
    public static UserResponse from(UserEntity entity) {
        return new UserResponse(
                entity.getId(),
                entity.getName(),
                entity.getEmail(),
                entity.getRole().name(),
                entity.getCreatedAt()
        );
    }
}
