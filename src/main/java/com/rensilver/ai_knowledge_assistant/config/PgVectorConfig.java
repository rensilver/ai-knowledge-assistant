package com.rensilver.ai_knowledge_assistant.config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore.PgDistanceType;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore.PgIndexType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Configures the {@link VectorStore} backed by PostgreSQL + pgvector.
 *
 * <p>{@code spring-ai-starter-vector-store-pgvector} can autoconfigure a
 * {@link PgVectorStore} bean on its own from {@code spring.ai.vectorstore.pgvector.*}
 * properties, but we define it explicitly here to:
 *
 * <ul>
 *   <li>Match the exact schema used by the {@code document_chunks} table
 *       described in the architecture doc (768-dim vectors from nomic-embed-text);</li>
 *   <li>Pin the distance metric (cosine) and index type (HNSW) used for
 *       {@code SimilaritySearchService};</li>
 *   <li>Keep table/schema naming explicit rather than relying on defaults,
 *       since {@code DocumentChunker} and {@code ContextRetriever} depend on it.</li>
 * </ul>
 *
 * <p>Requires the {@code vector} extension to be enabled on the database
 * (handled by the Docker init script / Flyway migration, not here).
 */
@Configuration
public class PgVectorConfig {

    @Value("${app.vectorstore.table-name:document_chunks}")
    private String vectorTableName;

    @Value("${app.vectorstore.schema-name:public}")
    private String schemaName;

    /**
     * nomic-embed-text produces 768-dimensional embeddings. This must match
     * the VECTOR(768) column defined on document_chunks.
     */
    @Value("${app.vectorstore.dimensions:768}")
    private int embeddingDimensions;

    /**
     * VectorStore used by RagService / ContextRetriever for similarity search
     * over document_chunks. Cosine distance is used since nomic-embed-text
     * embeddings are normalized for cosine similarity. HNSW gives faster
     * approximate search than IVFFlat at query time, at the cost of slower
     * index builds — acceptable here since inserts happen at upload time,
     * not on the hot chat path.
     *
     * @param jdbcTemplate      Postgres connection, autoconfigured from
     *                          spring.datasource.* properties
     * @param embeddingModel    Ollama embedding model (nomic-embed-text),
     *                          autoconfigured by spring-ai-starter-model-ollama
     */
    @Bean
    public VectorStore vectorStore(JdbcTemplate jdbcTemplate, EmbeddingModel embeddingModel) {
        return PgVectorStore.builder(jdbcTemplate, embeddingModel)
                .schemaName(schemaName)
                .vectorTableName(vectorTableName)
                .dimensions(embeddingDimensions)
                .distanceType(PgDistanceType.COSINE_DISTANCE)
                .indexType(PgIndexType.HNSW)
                .initializeSchema(false) // schema is owned by Flyway/init SQL, not Spring AI
                .removeExistingVectorStoreTable(false)
                .maxDocumentBatchSize(10_000)
                .build();
    }
}
