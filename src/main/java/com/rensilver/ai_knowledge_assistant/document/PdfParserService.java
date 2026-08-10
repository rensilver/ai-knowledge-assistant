package com.rensilver.ai_knowledge_assistant.document;

import com.rensilver.ai_knowledge_assistant.exception.DocumentProcessingException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Extracts text from a PDF's raw bytes using Apache PDFBox, ahead of chunking
 * (see {@code rag.DocumentChunker}) and indexing.
 *
 * <p>Text is extracted one page at a time rather than in a single pass, so each
 * chunk can carry the page it came from and answers can cite "page 12" rather
 * than just a filename.
 */
@Service
public class PdfParserService {

    public List<PdfPage> extractPages(byte[] pdfBytes) {
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            List<PdfPage> pages = new ArrayList<>(document.getNumberOfPages());

            for (int page = 1; page <= document.getNumberOfPages(); page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);

                String text = stripper.getText(document);
                // Image-only/blank pages extract to nothing; indexing them would
                // just add empty chunks for the embedding model to score.
                if (!text.isBlank()) {
                    pages.add(new PdfPage(page, text));
                }
            }

            return pages;
        } catch (IOException e) {
            throw new DocumentProcessingException("Failed to extract text from PDF", e);
        }
    }
}
