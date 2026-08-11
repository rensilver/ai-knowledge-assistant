# AI Knowledge Assistant

Corporate RAG chat assistant. Java 21, Spring Boot 4, Spring AI 2.0 (Ollama + pgvector), JWT auth. See `AI Knowledge Assistant project.txt` for the original architecture spec and roadmap (V1–V4).

## Running it

```bash
docker compose up -d          # postgres+pgvector, ollama, and a one-shot model pull
./mvnw spring-boot:run        # app on :8080, Flyway applies V1–V5 on boot
docker compose --profile full up   # ...or run the backend in a container too
```

`docker compose up` deliberately starts only the dependencies — the backend is behind the `full` profile so the usual dev loop (run from the IDE) doesn't rebuild an image every time.

For anything other than local dev, activate the `prod` profile (`SPRING_PROFILES_ACTIVE=prod`) and export a real secret first: `export JWT_SECRET="$(openssl rand -base64 32)"`. Without it the app refuses to start (see Key decisions).

## Status

- **Auth (V2 scope)**: done — register/login, JWT, `GlobalExceptionHandler`.
- **V1 RAG pipeline**: done — PDF upload (`document/`) → chunking + pgvector indexing (`rag/`, `vectorstore/`) → grounded chat (`chat/`).
- **V2 persistent history**: done — `JdbcChatHistoryRepository` over the `chat_history` table replaces the in-memory store; conversations survive restarts.
- **V3 citations + re-ranking**: done — answers cite `filename (page N)`; retrieval over-fetches and re-ranks (`ReRanker`).
- **V4 tool calling**: done — `POST /agent` lets the model call `searchDocuments` / `listDocuments` (`agent/KnowledgeBaseTools`) and, for general knowledge the documents don't cover, `searchExternalKnowledge` against Wikipedia (`agent/ExternalKnowledgeTools`).
- **Async ingestion**: done — `DocumentService.upload` returns as soon as the file is stored; `DocumentIngestionService` parses/chunks/embeds/indexes on a background pool and flips the row to `COMPLETED`/`FAILED`. `GET /documents/{id}` polls status.
- **Observability**: done — `CorrelationIdFilter` stamps every request (and the async ingestion thread) with a request id via MDC; Micrometer timers on the retrieval, chat-answer, and agent-turn paths (`/actuator/metrics`); structured JSON logging (Boot's built-in ECS format) in the `prod` profile.
- **Migrations**: V1–V5 verified applying against pgvector, both fresh and incrementally.
- **API docs**: done — Swagger UI / OpenAPI 3 wired up via `springdoc-openapi-starter-webmvc-ui` (`OpenApiConfig`), with a JWT bearer security scheme and `@Operation`/`@ApiResponse` annotations documenting Auth, Documents, Chat, and Agent.
- **Tests**: 38 unit + 11 integration (`./mvnw verify`; integration tests need Docker).

## Key decisions worth knowing

- **Docker Postgres must be `pgvector/pgvector`, never plain `postgres`.** `V2__create_extensions.sql` does `CREATE EXTENSION vector`, which a stock image cannot satisfy. This bit the project once already; `PgVectorTestSupport` pins the right image for tests.
- **`springdoc-openapi-starter-webmvc-ui` must be on its 3.x line for Spring Boot 4 / Spring Framework 7.** The 2.x line — even 2.6.0, what this project had pinned before Swagger was actually wired up — is binary-incompatible and throws `NoSuchMethodError` on `ControllerAdviceBean.<init>(Object)` the moment `/v3/api-docs` is requested. The app starts fine on either version, since springdoc's incompatible code path isn't touched until something hits the docs endpoint, which is why this sat unnoticed for as long as the dependency was pinned but unused in `pom.xml`.
- **Retrieved context goes in the *system* message, not appended to the user's question.** `MessageChatMemoryAdvisor` persists only the last user message, so this keeps history storing the question rather than a copy of every chunk retrieved for it. Verified against the advisor's `before()`, which stores `getLastUserOrToolResponseMessage()`.
- **`chat_history` is not Spring AI's `SPRING_AI_CHAT_MEMORY` schema.** Theirs keys on a single `conversation_id VARCHAR(36)`, too narrow for this app's user-namespaced key (`"<userId>:<conversationId>"`, 73 chars). Two real UUID columns also give per-user listing and `ON DELETE CASCADE`.
- **Chunking happens per page** (`DocumentChunker`), so a chunk never straddles a page boundary and can be attributed to exactly one page. Costs slightly undersized chunks at page ends.
- **The SQL agent tool exposes a fixed listing, not model-supplied SQL** — a "run this query" tool would hand an injection primitive to whatever the model can be talked into emitting. The Wikipedia tool similarly never takes a raw URL — only a topic string that gets encoded into a fixed REST path template.
- **`spring-ai-advisors-vector-store` has no 2.0.0 GA** (latest is 2.0.0-M8), so `QuestionAnswerAdvisor` is unavailable. `RagService` does retrieval/augmentation by hand instead; V4 uses `@Tool` + `defaultTools(...)`, which are in 2.0.0 proper.
- **The JWT secret has no default outside dev.** `application.yml`'s `${JWT_SECRET:...}` default only applies without an active profile. `application-prod.yml` overrides it with `${JWT_SECRET}` (no fallback), so Spring's placeholder resolution fails fast at startup on the `prod` profile if the env var isn't set, instead of silently running with the secret checked into source control.
- **No explicit `DaoAuthenticationProvider` bean in `SecurityConfig`.** Spring Security's global `AuthenticationManagerBuilder` auto-wires one from the single `UserDetailsService` bean + the `PasswordEncoder` bean; declaring it explicitly was pure duplication (and the source of a Boot startup warning).
- **A JWT API needs an explicit `AuthenticationEntryPoint`.** Without one, Spring Security's default for a request with no credentials at all is `Http403ForbiddenEntryPoint` (403) — technically wrong for "not authenticated" (401), and it's what tripped up `AuthControllerIT` until `SecurityConfig` set `HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)` explicitly.
- **`@Async` doesn't propagate MDC.** `DocumentIngestionService` runs on `AsyncConfig`'s executor, a different thread than the one `CorrelationIdFilter` stamped — a `TaskDecorator` copies the MDC context map across that boundary so ingestion logs still carry the request id.
- **Boot 4 relocations that cost time:** test slices moved to per-technology modules (`@JdbcTest` is now in `spring-boot-jdbc-test`, package `org.springframework.boot.jdbc.test.autoconfigure`; `@WebMvcTest` is in `spring-boot-webmvc-test`, package `org.springframework.boot.webmvc.test.autoconfigure`), Testcontainers 2.x renamed modules to `testcontainers-postgresql` / `testcontainers-junit-jupiter`, `@MockBean` is gone in favor of `@MockitoBean` (`org.springframework.test.context.bean.override.mockito`), and `TestRestTemplate` moved to its own `spring-boot-resttestclient` module — needs `@AutoConfigureTestRestTemplate` explicitly now, it's no longer auto-registered just by `@SpringBootTest(webEnvironment = RANDOM_PORT)`.
- **`@WebMvcTest` picks up `JwtFilter` (a servlet `Filter` bean) but not its own dependencies** (`JwtService`, `UserDetailsServiceImpl` — plain `@Service` beans outside the web slice), so those need `@MockitoBean` stand-ins in controller slice tests even though the tests never exercise them (`@WithMockUser` bypasses the filter's token handling entirely).

## Next steps

1. **Frontend.** Unstarted, and deliberately deferred — likely a separate project consuming this API.
2. **Wire up a metrics backend.** The RAG/ingestion path is instrumented (Micrometer timers) and exposed at `/actuator/metrics`, but nothing scrapes it yet — no Prometheus registry dependency, no dashboard, no tracing (Micrometer Tracing + OTLP) despite the structured logs already carrying a request id that would correlate with it.
3. **Minor:** `application.yml`'s `logging.pattern.console` is dev-only convenience; `application-prod.yml`'s `logging.structured.format.console: ecs` replaces it in prod but hasn't been pointed at a real log shipper (Filebeat/Fluentd/etc.) — right now it just changes the console's format.
4. **Wikipedia tool has no caching or rate-limit handling.** Fine for demo/dev use; repeated identical lookups within a conversation re-hit the API every time, and Wikipedia's rate limit for anonymous non-commercial use isn't accounted for.
