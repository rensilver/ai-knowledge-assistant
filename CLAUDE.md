# AI Knowledge Assistant

Corporate RAG chat assistant. Java 21, Spring Boot 4, Spring AI 2.0 (Ollama + pgvector), JWT auth. See `AI Knowledge Assistant project.txt` for the original architecture spec and roadmap (V1–V4).

## Running it

```bash
docker compose up -d          # postgres+pgvector, ollama, and a one-shot model pull
./mvnw spring-boot:run        # app on :8080, Flyway applies V1–V5 on boot
docker compose --profile full up   # ...or run the backend in a container too
```

`docker compose up` deliberately starts only the dependencies — the backend is behind the `full` profile so the usual dev loop (run from the IDE) doesn't rebuild an image every time.

## Status

- **Auth (V2 scope)**: done — register/login, JWT, `GlobalExceptionHandler`.
- **V1 RAG pipeline**: done — PDF upload (`document/`) → chunking + pgvector indexing (`rag/`, `vectorstore/`) → grounded chat (`chat/`).
- **V2 persistent history**: done — `JdbcChatHistoryRepository` over the `chat_history` table replaces the in-memory store; conversations survive restarts.
- **V3 citations + re-ranking**: done — answers cite `filename (page N)`; retrieval over-fetches and re-ranks (`ReRanker`).
- **V4 tool calling**: partly done — `POST /agent` lets the model call `searchDocuments` / `listDocuments` itself (`agent/`). The spec's third agent (external API) is not built — see Next steps.
- **Migrations**: V1–V5 verified applying against pgvector, both fresh and incrementally.
- **Tests**: 13 unit + 6 integration (`./mvnw verify`; integration tests need Docker).

## Key decisions worth knowing

- **Docker Postgres must be `pgvector/pgvector`, never plain `postgres`.** `V2__create_extensions.sql` does `CREATE EXTENSION vector`, which a stock image cannot satisfy. This bit the project once already; `PgVectorTestSupport` pins the right image for tests.
- **Retrieved context goes in the *system* message, not appended to the user's question.** `MessageChatMemoryAdvisor` persists only the last user message, so this keeps history storing the question rather than a copy of every chunk retrieved for it. Verified against the advisor's `before()`, which stores `getLastUserOrToolResponseMessage()`.
- **`chat_history` is not Spring AI's `SPRING_AI_CHAT_MEMORY` schema.** Theirs keys on a single `conversation_id VARCHAR(36)`, too narrow for this app's user-namespaced key (`"<userId>:<conversationId>"`, 73 chars). Two real UUID columns also give per-user listing and `ON DELETE CASCADE`.
- **Chunking happens per page** (`DocumentChunker`), so a chunk never straddles a page boundary and can be attributed to exactly one page. Costs slightly undersized chunks at page ends.
- **The SQL agent tool exposes a fixed listing, not model-supplied SQL** — a "run this query" tool would hand an injection primitive to whatever the model can be talked into emitting.
- **`spring-ai-advisors-vector-store` has no 2.0.0 GA** (latest is 2.0.0-M8), so `QuestionAnswerAdvisor` is unavailable. `RagService` does retrieval/augmentation by hand instead; V4 uses `@Tool` + `defaultTools(...)`, which are in 2.0.0 proper.
- **Boot 4 relocations that cost time:** test slices moved to per-technology modules (`@JdbcTest` is now in `spring-boot-jdbc-test`, package `org.springframework.boot.jdbc.test.autoconfigure`), and Testcontainers 2.x renamed modules to `testcontainers-postgresql` / `testcontainers-junit-jupiter`.

## Next steps

1. **Set a real JWT secret outside dev.** `application.yml` now has a working dev default via `${JWT_SECRET:...}`, but it is committed and therefore public. Every non-local environment must export `JWT_SECRET="$(openssl rand -base64 32)"`.
2. **V4 remainder: the external-API agent.** The spec's third agent needs a concrete API to call before it can be built — the tool itself is a few lines once that's decided (`agent/KnowledgeBaseTools` shows the shape).
3. **Widen test coverage.** Current tests cover the re-ranker, prompt building, conversation keys, and chat-history persistence. Untested: the auth flow end to end, `DocumentService` ingestion, `RagService`, and the controllers. `DocumentService.upload` in particular has branches (unsupported type, extraction failure → `FAILED`) worth pinning down.
4. **Uploads are processed synchronously inside the request.** A large PDF makes the HTTP call hang for its whole parse+embed cycle. Moving ingestion to `@Async`/a queue and letting `DocumentStatus.PROCESSING` mean something is the natural fix.
5. **Observability** is in the original spec but unstarted beyond the actuator dependency — no metrics, tracing, or structured logging around the RAG path.
6. **Frontend.** Unstarted, and deliberately deferred — likely a separate project consuming this API.
7. **Minor:** Spring Security logs a warning that the `DaoAuthenticationProvider` bean disables `UserDetailsService` auto-configuration. Behaviour is correct; the bean is just redundant with what Boot would wire itself.
