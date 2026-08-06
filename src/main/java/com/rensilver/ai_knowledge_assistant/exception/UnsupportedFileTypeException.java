package com.rensilver.ai_knowledge_assistant.exception;

/**
 * Thrown on upload when the file isn't a PDF — the only format the V1
 * ingestion pipeline (PdfParserService) knows how to extract text from.
 */
public class UnsupportedFileTypeException extends RuntimeException {
    public UnsupportedFileTypeException(String contentType) {
        super("Unsupported file type: " + contentType + ". Only application/pdf is accepted.");
    }
}
