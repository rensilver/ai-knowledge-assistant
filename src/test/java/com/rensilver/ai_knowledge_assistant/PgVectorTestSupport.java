package com.rensilver.ai_knowledge_assistant;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Base class for tests that need a real database.
 *
 * <p>Must be the {@code pgvector} image, not plain {@code postgres}: the
 * migrations create the {@code vector} extension and an HNSW index, both of
 * which only exist in an image with pgvector compiled in. Running these against
 * plain {@code postgres:16} is exactly the failure this project hit in dev.
 *
 * <p>The container is {@code static}, so one instance is shared by every test
 * class that extends this rather than paying container startup per class.
 */
@Testcontainers
public abstract class PgVectorTestSupport {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres")
    );
}
