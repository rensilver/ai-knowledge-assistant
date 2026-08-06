package com.rensilver.ai_knowledge_assistant.entity;

/**
 * Lifecycle of an uploaded document as it moves through the RAG ingestion
 * pipeline (parse -> chunk -> embed -> index).
 */
public enum DocumentStatus {
    PROCESSING,
    COMPLETED,
    FAILED
}
