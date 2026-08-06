package com.rensilver.ai_knowledge_assistant.document;

import com.rensilver.ai_knowledge_assistant.exception.DocumentProcessingException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

import java.io.IOException;

/**
 * Extracts plain text from a PDF's raw bytes using Apache PDFBox, ahead of
 * chunking (see {@code rag.DocumentChunker}) and indexing.
 */
@Service
public class PdfParserService {

    public String extractText(byte[] pdfBytes) {
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            return new PDFTextStripper().getText(document);
        } catch (IOException e) {
            throw new DocumentProcessingException("Failed to extract text from PDF", e);
        }
    }
}
