package com.rensilver.ai_knowledge_assistant.entity;

/**
 * Authorization roles. Kept deliberately small for the MVP — extend here
 * (not with per-endpoint flags) if finer-grained permissions are needed later.
 */
public enum Role {
    ADMIN,
    USER;

    /** Spring Security authority form, e.g. {@code ROLE_ADMIN}. */
    public String authority() {
        return "ROLE_" + name();
    }
}
