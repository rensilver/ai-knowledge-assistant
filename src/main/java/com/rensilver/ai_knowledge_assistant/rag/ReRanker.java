package com.rensilver.ai_knowledge_assistant.rag;

import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Re-orders the candidates returned by vector search before they are handed to
 * the model, so the few chunks that actually make it into the prompt are the
 * most useful ones.
 *
 * <p>Pure vector similarity is recall-oriented and blurs exact tokens: product
 * names, acronyms, error codes and version numbers all embed close to their
 * neighbours, so a chunk that literally contains the term the user asked about
 * can rank below one that merely discusses the same topic. Blending the vector
 * score with how many of the query's terms literally occur in the chunk pulls
 * those exact matches back up.
 *
 * <p>This is a lexical re-ranker, not a cross-encoder. A cross-encoder scores
 * the (query, chunk) pair jointly and would rank better, but it means running a
 * second model per query; this costs nothing beyond string work and needs no
 * extra model pulled into Ollama.
 */
@Service
public class ReRanker {

    /** Tokens shorter than this are near-useless as match signal ("a", "of", "is"). */
    private static final int MIN_TERM_LENGTH = 3;

    private final double vectorWeight;

    public ReRanker(@Value("${app.rag.vector-weight:0.75}") double vectorWeight) {
        this.vectorWeight = vectorWeight;
    }

    public List<Document> rerank(String query, List<Document> candidates, int topK) {
        if (candidates.size() <= 1) {
            return candidates;
        }

        Set<String> queryTerms = terms(query);

        return candidates.stream()
                .sorted(Comparator.comparingDouble((Document chunk) -> score(chunk, queryTerms)).reversed())
                .limit(topK)
                .toList();
    }

    private double score(Document chunk, Set<String> queryTerms) {
        return vectorWeight * vectorScore(chunk) + (1 - vectorWeight) * lexicalOverlap(chunk, queryTerms);
    }

    /** Set by PgVectorStore as {@code 1 - cosine distance}, so higher is closer. */
    private double vectorScore(Document chunk) {
        Double score = chunk.getScore();
        return score != null ? score : 0.0;
    }

    /**
     * Fraction of the query's distinct terms that literally appear in the
     * chunk, which keeps the two blended scores on the same 0..1 scale.
     */
    private double lexicalOverlap(Document chunk, Set<String> queryTerms) {
        if (queryTerms.isEmpty()) {
            return 0.0;
        }

        Set<String> chunkTerms = terms(chunk.getText());
        long matched = queryTerms.stream().filter(chunkTerms::contains).count();
        return (double) matched / queryTerms.size();
    }

    private Set<String> terms(String text) {
        if (text == null || text.isBlank()) {
            return Set.of();
        }

        return Arrays.stream(text.toLowerCase(Locale.ROOT).split("[^\\p{IsAlphabetic}\\p{IsDigit}]+"))
                .filter(term -> term.length() >= MIN_TERM_LENGTH)
                .collect(Collectors.toSet());
    }
}
