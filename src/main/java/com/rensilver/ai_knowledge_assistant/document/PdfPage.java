package com.rensilver.ai_knowledge_assistant.document;

/**
 * One page of extracted PDF text.
 *
 * @param number 1-based page number, as a reader would cite it
 * @param text   plain text extracted from that page
 */
public record PdfPage(int number, String text) {
}
