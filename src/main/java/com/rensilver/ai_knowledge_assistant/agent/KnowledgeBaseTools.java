package com.rensilver.ai_knowledge_assistant.agent;

import com.rensilver.ai_knowledge_assistant.dto.SourceReference;
import com.rensilver.ai_knowledge_assistant.rag.ContextRetriever;
import com.rensilver.ai_knowledge_assistant.repository.DocumentRepository;
import org.springframework.ai.document.Document;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Tools the model may call on its own during an {@code /agent} turn (V4).
 *
 * <p>Where {@code RagService} always retrieves first and answers once, these
 * let the model decide <em>whether</em> to search, search repeatedly with
 * refined queries, or answer a question about the library itself ("how many
 * documents do we have?") that no amount of semantic search would answer.
 *
 * <p>Each method's {@code @Tool} description is the only thing the model sees
 * when choosing, so they describe when to use the tool, not how it works.
 */
@Component
public class KnowledgeBaseTools {

    private static final DateTimeFormatter UPLOAD_DATE = DateTimeFormatter.ISO_LOCAL_DATE.withZone(ZoneOffset.UTC);

    private final ContextRetriever contextRetriever;
    private final DocumentRepository documentRepository;

    public KnowledgeBaseTools(ContextRetriever contextRetriever, DocumentRepository documentRepository) {
        this.contextRetriever = contextRetriever;
        this.documentRepository = documentRepository;
    }

    @Tool(description = """
            Search the company's uploaded documents for passages relevant to a
            question or topic. Use this for any question about the content of
            company documents. Returns passages with the document name and page
            number to cite.
            """)
    public String searchDocuments(
            @ToolParam(description = "The topic or question to search for, in natural language")
            String query
    ) {
        List<Document> chunks = contextRetriever.retrieve(query);

        if (chunks.isEmpty()) {
            return "No relevant passages found in the knowledge base for: " + query;
        }

        return chunks.stream()
                .map(chunk -> "Source: %s%n%s".formatted(
                        SourceReference.from(chunk).describe(),
                        chunk.getText()))
                .collect(Collectors.joining("\n\n"));
    }

    /**
     * Deliberately exposes a fixed, parameterless listing rather than letting
     * the model supply SQL. A "run this query" tool would hand an injection
     * primitive to whatever text the user can talk the model into emitting;
     * the questions this actually needs to answer are closed-ended enough to
     * enumerate.
     */
    @Tool(description = """
            List the documents currently in the knowledge base, with their upload
            date, size and processing status. Use this to answer questions about
            which documents exist or how many there are, rather than about their
            contents.
            """)
    @Transactional(readOnly = true)
    public String listDocuments() {
        List<String> documents = documentRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(document -> "- %s (uploaded %s, %d KB, status %s)".formatted(
                        document.getFilename(),
                        UPLOAD_DATE.format(document.getCreatedAt()),
                        document.getSizeBytes() / 1024,
                        document.getStatus()))
                .toList();

        if (documents.isEmpty()) {
            return "The knowledge base is empty — no documents have been uploaded yet.";
        }

        return "%d document(s) in the knowledge base:%n%s"
                .formatted(documents.size(), String.join("\n", documents));
    }
}
