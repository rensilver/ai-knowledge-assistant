package com.rensilver.ai_knowledge_assistant;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Wiring smoke test: every bean resolves and all migrations apply.
 *
 * <p>Needs a real database (see {@link PgVectorTestSupport}) but not a running
 * Ollama — the model beans are constructed without connecting, and
 * {@code PgVectorConfig} pins the embedding dimensions explicitly rather than
 * asking the embedding model for them at startup.
 */
@SpringBootTest
class AiKnowledgeAssistantApplicationTests extends PgVectorTestSupport {

	@Test
	void contextLoads() {
	}

}
