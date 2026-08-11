package com.rensilver.ai_knowledge_assistant.controller;

import com.rensilver.ai_knowledge_assistant.PgVectorTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards against the exact regression the final Swagger/OpenAPI review found:
 * this project had {@code springdoc-openapi-starter-webmvc-ui} pinned to 2.6.0
 * for a while before the Swagger branch actually wired anything up, and 2.x is
 * binary-incompatible with Spring Boot 4 / Spring Framework 7 — it throws
 * {@code NoSuchMethodError} on {@code ControllerAdviceBean.<init>(Object)}.
 * Crucially, the app starts fine under either version; the incompatible code
 * path is only exercised when something actually calls {@code /v3/api-docs},
 * which is why it previously went unnoticed — it's the endpoint call that
 * fails (would have been a 500 under 2.6.0), not application startup. This
 * test hits the real endpoint through a real Spring context so a future
 * springdoc/Boot bump that reintroduces the incompatibility fails CI instead
 * of going unnoticed again.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class OpenApiIT extends PgVectorTestSupport {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void apiDocsEndpointServesTheGeneratedOpenApiDocumentWithAllTags() {
        ResponseEntity<String> response = restTemplate.getForEntity("/v3/api-docs", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .contains("Auth")
                .contains("Documents")
                .contains("Chat")
                .contains("Agent");
    }
}
