package com.rensilver.ai_knowledge_assistant.exception;

/**
 * Thrown when a document's raw bytes can't be turned into indexable text
 * (corrupt/encrypted PDF, extraction failure) or the resulting text can't
 * be chunked/embedded/stored in the vector store.
 */
public class DocumentProcessingException extends RuntimeException {
    public DocumentProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}
