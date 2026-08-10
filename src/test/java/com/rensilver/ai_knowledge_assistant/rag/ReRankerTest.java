package com.rensilver.ai_knowledge_assistant.rag;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ReRankerTest {

    private static Document chunk(String id, String text, double vectorScore) {
        return Document.builder()
                .id(id)
                .text(text)
                .metadata(Map.of())
                .score(vectorScore)
                .build();
    }

    @Test
    void promotesTheChunkContainingTheQueryTermOverACloserButVaguerOne() {
        ReRanker reRanker = new ReRanker(0.5);

        Document vague = chunk("vague", "The platform stores embeddings for semantic search.", 0.90);
        Document exact = chunk("exact", "pgvector is the extension used to store embeddings.", 0.80);

        List<Document> ranked = reRanker.rerank("what is pgvector", List.of(vague, exact), 2);

        // Vector search alone would rank `vague` first; the literal term match
        // is what should pull `exact` up.
        assertThat(ranked).extracting(Document::getId).containsExactly("exact", "vague");
    }

    @Test
    void fallsBackToVectorOrderWhenNoTermsMatch() {
        ReRanker reRanker = new ReRanker(0.75);

        Document closer = chunk("closer", "Deployment topology and rollout strategy.", 0.91);
        Document further = chunk("further", "Budget approvals and procurement.", 0.55);

        List<Document> ranked = reRanker.rerank("unrelated question", List.of(further, closer), 2);

        assertThat(ranked).extracting(Document::getId).containsExactly("closer", "further");
    }

    @Test
    void trimsToTopK() {
        ReRanker reRanker = new ReRanker(0.75);

        List<Document> candidates = List.of(
                chunk("a", "alpha", 0.9),
                chunk("b", "beta", 0.8),
                chunk("c", "gamma", 0.7)
        );

        assertThat(reRanker.rerank("alpha", candidates, 2)).hasSize(2);
    }

    @Test
    void toleratesChunksWithoutAVectorScore() {
        ReRanker reRanker = new ReRanker(0.75);

        // similaritySearch always sets a score, but Document doesn't require
        // one — a null must not blow up the comparator.
        Document unscored = Document.builder().id("unscored").text("pgvector notes").build();
        Document scored = chunk("scored", "unrelated text", 0.6);

        List<Document> ranked = reRanker.rerank("pgvector", List.of(unscored, scored), 2);

        // A missing score counts as 0.0, so the unscored chunk keeps only its
        // lexical share (0.25 * 1.0) and still loses to 0.75 * 0.6.
        assertThat(ranked).extracting(Document::getId).containsExactly("scored", "unscored");
    }

    @Test
    void returnsSingleCandidateUntouched() {
        ReRanker reRanker = new ReRanker(0.75);
        Document only = chunk("only", "anything", 0.1);

        assertThat(reRanker.rerank("query", List.of(only), 5)).containsExactly(only);
    }
}
