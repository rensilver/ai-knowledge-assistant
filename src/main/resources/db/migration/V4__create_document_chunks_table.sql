-- Schema must match exactly what Spring AI's PgVectorStore expects
-- (id/content/metadata/embedding), since PgVectorConfig disables
-- initializeSchema and defers table ownership to Flyway instead.
-- Dimensions (768) match nomic-embed-text, configured via
-- app.vectorstore.dimensions.
CREATE TABLE IF NOT EXISTS document_chunks (
    id        uuid DEFAULT uuid_generate_v4() PRIMARY KEY,
    content   text,
    metadata  json,
    embedding vector(768)
);

-- Approximate nearest-neighbor search on the embedding column, using the
-- cosine ops class since PgVectorConfig is configured with COSINE_DISTANCE.
CREATE INDEX IF NOT EXISTS document_chunks_embedding_hnsw_idx
    ON document_chunks
    USING hnsw (embedding vector_cosine_ops);

-- VectorStoreService filters/deletes chunks by document_id (stored inside
-- the metadata JSON) whenever a document is deleted or re-indexed, so index
-- that access path explicitly rather than relying on a sequential scan.
CREATE INDEX IF NOT EXISTS document_chunks_metadata_gin_idx
    ON document_chunks
    USING gin ((metadata::jsonb) jsonb_path_ops);
