-- Required by PgVectorStore (spring-ai-starter-vector-store-pgvector):
--   vector      -> the `vector` column type and similarity operators
--   uuid-ossp   -> uuid_generate_v4(), used as the default id generator
--                  for document_chunks (matches PgVectorStore's own schema)
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
