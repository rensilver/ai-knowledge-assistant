package com.rensilver.ai_knowledge_assistant.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Returned by both POST /auth/register and POST /auth/login on success.
 *
 * @param token      signed JWT to send as {@code Authorization: Bearer <token>}
 * @param expiresIn  token lifetime in milliseconds (mirrors app.jwt.expiration-ms),
 *                   so the frontend can proactively refresh/redirect to login
 * @param name       convenience fields so the frontend doesn't need a second
 * @param email      call just to render "logged in as ..."
 * @param role
 */
public record AuthResponse(
        @Schema(description = "signed JWT to send as `Authorization: Bearer <token>`")
        String token,
        @Schema(description = "token lifetime in milliseconds (mirrors app.jwt.expiration-ms), "
                + "so the frontend can proactively refresh/redirect to login")
        long expiresIn,
        @Schema(description = "convenience field so the frontend doesn't need a second call just to render \"logged in as ...\"")
        String name,
        @Schema(description = "convenience field so the frontend doesn't need a second call just to render \"logged in as ...\"")
        String email,
        String role
) {
}
