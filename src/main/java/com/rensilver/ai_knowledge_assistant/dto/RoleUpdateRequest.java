package com.rensilver.ai_knowledge_assistant.dto;

import com.rensilver.ai_knowledge_assistant.entity.Role;
import jakarta.validation.constraints.NotNull;

/**
 * Payload for PATCH /users/{id}/role.
 */
public record RoleUpdateRequest(
        @NotNull(message = "Role is required")
        Role role
) {
}
