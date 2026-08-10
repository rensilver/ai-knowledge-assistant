package com.rensilver.ai_knowledge_assistant.rag;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PromptAugmentationServiceTest {

    private final PromptAugmentationService service = new PromptAugmentationService();

    @Test
    void labelsEachChunkWithItsFilenameAndPage() {
        Document chunk = new Document("Horizontal scaling is preferred.", Map.of(
                DocumentChunker.DOCUMENT_ID, "doc-1",
                DocumentChunker.FILENAME, "AWS Architecture Guide.pdf",
                DocumentChunker.PAGE, 12
        ));

        String prompt = service.systemPrompt(List.of(chunk));

        assertThat(prompt)
                .contains("AWS Architecture Guide.pdf (page 12)")
                .contains("Horizontal scaling is preferred.");
    }

    @Test
    void tellsTheModelToRefuseWhenNothingWasRetrieved() {
        String prompt = service.systemPrompt(List.of());

        assertThat(prompt).contains("No relevant context was found");
        // Without this the model happily answers corporate questions from its
        // pretraining, which is exactly what a grounded assistant must not do.
        assertThat(prompt).contains("do not answer from general knowledge");
    }

    @Test
    void survivesChunksIndexedBeforePageTrackingExisted() {
        Document legacyChunk = new Document("Older indexed content.", Map.of(
                DocumentChunker.FILENAME, "legacy.pdf"
        ));

        String prompt = service.systemPrompt(List.of(legacyChunk));

        assertThat(prompt).contains("legacy.pdf").doesNotContain("page null");
    }
}
