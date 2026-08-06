# AI Knowledge Assistant

Corporate RAG chat assistant. Java 21, Spring Boot 4, Spring AI 2.0 (Ollama + pgvector), JWT auth. See `AI Knowledge Assistant project.txt` for the original architecture spec and roadmap (V1–V4).

## Status

- **Auth (V2 scope)**: done — register/login, JWT, `GlobalExceptionHandler`.
- **V1 RAG pipeline**: done — PDF upload (`document/`) → chunking + pgvector indexing (`rag/`, `vectorstore/`) → grounded chat with per-user conversation memory (`chat/`).
- **Migrations**: Flyway wired and validated (`V1` users table confirmed applying against a live container). `V2` (extensions) onward blocked — see Next steps.

## Next steps

1. **Unblock pgvector locally.** The dev Postgres container is a plain `postgres:16` image without the `vector` extension compiled in, so `V2__create_extensions.sql` fails on `CREATE EXTENSION vector`. Either switch it to `pgvector/pgvector:pg16` or install the extension into the existing container, then re-run the app so `V2`–`V4` apply.
2. **Set a real JWT secret.** `application.yml`'s `app.jwt.secret` is still the placeholder `<base64-encoded, 256-bit+ key>` — `JwtService` will fail to Base64-decode it the moment a token is actually issued. Generate one with `openssl rand -base64 32`.
3. **Create `docker/` + `docker-compose.yml`** (postgres+pgvector, ollama, backend) — referenced in the architecture doc but never scaffolded. Would also fix #1 reproducibly for anyone else setting up the project.
4. **V2 roadmap remainder: persistent chat history.** `ChatMemory` (in `OllamaConfig`) is in-memory only (`InMemoryChatMemoryRepository`) — conversations are lost on restart. Swap it for a JDBC-backed repository against a new `chat_history` table (own migration) once that's wanted.
5. **V3 roadmap: page-level citations.** `ChatResponse.sources()` currently cites by filename only. `PdfParserService` uses `PDFTextStripper` without per-page tracking — extracting per-page text and carrying a `page` field through `DocumentChunker`'s metadata would be needed for "page 12"-style citations. Re-ranking also not started.
6. **V4 roadmap: AI agents / tool calling.** Not started — needs picking a Spring AI 2.0-compatible advisor/tool-calling approach (the `spring-ai-advisors-vector-store` dependency originally scaffolded for this has no 2.0.0 GA release; check what replaced it before resuming this).
7. **Frontend.** Entirely unstarted (`frontend/` in the architecture doc).
8. **Tests.** No automated tests yet, despite `spring-boot-starter-*-test` and `testcontainers-postgresql` already in `pom.xml`. Integration tests should use a `pgvector/pgvector` Testcontainers image, not plain `postgres`, given #1.
9. **Known tradeoff to revisit:** `RagService` stores the full augmented prompt (retrieved context + question) into `ChatMemory` each turn, not just the raw question. Simple and matches how Spring AI's own `QuestionAnswerAdvisor` works, but it means the 20-message memory window (`OllamaConfig`) fills up faster in long conversations. Revisit if that becomes a problem.
