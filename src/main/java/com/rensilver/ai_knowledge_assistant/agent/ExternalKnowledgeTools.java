package com.rensilver.ai_knowledge_assistant.agent;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * The V4 "third agent": a tool over a public external API, for general
 * knowledge that would never be in the company's own documents. Unlike
 * {@link KnowledgeBaseTools}, this is the one tool here that can answer from
 * outside the knowledge base — the agent's system prompt (see
 * {@code OllamaConfig}) is responsible for only reaching for it after
 * {@code searchDocuments} comes up empty, and for disclosing the source.
 *
 * <p>Wikipedia's summary endpoint needs no API key and returns plain-text
 * extracts, so there's no HTML to parse and no billing/rate-limit contract to
 * manage for a demo-scale tool.
 */
@Component
public class ExternalKnowledgeTools {

    private final RestClient restClient;

    public ExternalKnowledgeTools(
            RestClient.Builder builder,
            @Value("${app.external.wikipedia.base-url:https://en.wikipedia.org/api/rest_v1}") String baseUrl
    ) {
        this.restClient = builder.baseUrl(baseUrl).build();
    }

    @Tool(description = """
            Look up a short general-knowledge summary of a topic on Wikipedia.
            Only use this for general/world knowledge (e.g. "what is pgvector?",
            "who was Alan Turing?") after searchDocuments has already been tried
            and found nothing relevant. Never use it for questions about internal
            company policy, processes, or data. Results come from outside the
            company's documents, so always tell the user the answer came from
            Wikipedia rather than the knowledge base.
            """)
    public String searchExternalKnowledge(
            @ToolParam(description = "The topic to look up, e.g. 'pgvector' or 'HTTP status code 429'")
            String topic
    ) {
        if (topic == null || topic.isBlank()) {
            return "No topic given.";
        }

        // Wikipedia titles use underscores for spaces; RestClient's URI template
        // expansion percent-encodes whatever is left, so no manual encoding here.
        String title = topic.trim().replace(' ', '_');

        try {
            WikipediaSummary summary = restClient.get()
                    .uri("/page/summary/{title}", title)
                    .retrieve()
                    .body(WikipediaSummary.class);

            if (summary == null || summary.extract() == null || summary.extract().isBlank()) {
                return "Wikipedia has no summary for: " + topic;
            }
            return summary.extract();
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 404) {
                return "No Wikipedia article found for: " + topic;
            }
            return "Could not reach Wikipedia to look up: " + topic;
        }
    }

    private record WikipediaSummary(String title, String extract) {
    }
}
