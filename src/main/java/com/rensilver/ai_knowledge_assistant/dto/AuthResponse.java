package com.rensilver.ai_knowledge_assistant.dto;

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
        String token,
        long expiresIn,
        String name,
        String email,
        String role
) {
}
