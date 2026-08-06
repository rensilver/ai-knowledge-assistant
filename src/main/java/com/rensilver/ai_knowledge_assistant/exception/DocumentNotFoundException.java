package com.rensilver.ai_knowledge_assistant.exception;

import java.util.UUID;

public class DocumentNotFoundException extends RuntimeException {
    public DocumentNotFoundException(UUID id) {
        super("No document found with id: " + id);
    }
}
