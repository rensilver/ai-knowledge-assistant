# Swagger / OpenAPI documentation — design

## Context

`springdoc-openapi-starter-webmvc-ui` (2.6.0) is already a dependency in `pom.xml`,
and `SecurityConfig` already permits `/swagger-ui/**` and `/v3/api-docs/**`
unauthenticated. Neither piece was ever finished: there is no `OpenAPI` bean
(no title/description/version, no JWT bearer scheme for the Swagger UI
"Authorize" button), and none of the four controllers (`AuthController`,
`ChatController`, `AgentController`, `DocumentController`) carry any
`@Tag`/`@Operation`/`@ApiResponse` annotations. Springdoc would still generate
a bare spec from the Spring MVC annotations alone, but protected endpoints
couldn't be exercised from the UI (no way to supply a bearer token) and the
docs would carry no request/response/error detail.

## Goals

- Swagger UI reachable and usable end-to-end, including authorizing with a
  JWT and calling protected endpoints from the browser.
- Every endpoint documented: summary, request/response shapes, and the
  specific error statuses it can actually return (matching
  `GlobalExceptionHandler`'s behavior, not a generic 4xx/5xx).
- No new dependencies, no `SecurityConfig` changes — both already support
  this.

## Design

### 1. `OpenApiConfig` (new `config/OpenApiConfig.java`)

A single `@Bean OpenAPI` providing:
- `Info`: title "AI Knowledge Assistant API", a short description, and
  version `0.0.1-SNAPSHOT` as a literal string constant, matching
  `pom.xml`. (Wiring the real POM version in would need the
  `spring-boot-maven-plugin` `build-info` goal or resource filtering, neither
  of which is configured — not worth adding just for a docs label.)
- A `SecurityScheme` named `bearerAuth` — `HTTP` type, scheme `bearer`,
  bearer format `JWT` — registered as a global `SecurityRequirement` so every
  operation defaults to requiring it and shows a padlock in the UI.

### 2. Per-controller tags and operation docs

- **`AuthController`** — `@Tag(name = "Auth")`. `register` and `login` get
  `@Operation(security = {})` (empty array overrides the global requirement,
  since `/auth/**` is public per `SecurityConfig`) plus `@ApiResponse`s:
  - `register`: 201 (created) / 409 (`EmailAlreadyInUseException`) / 400
    (validation)
  - `login`: 200 / 401 (`BadCredentialsException`) / 400 (validation)
- **`DocumentController`** — `@Tag(name = "Documents")`.
  - `upload`: 201 / 400 (`UnsupportedFileTypeException`) / 413
    (`MaxUploadSizeExceededException`) / 422 (`DocumentProcessingException`)
  - `list`: 200
  - `get`: 200 / 404 (`DocumentNotFoundException`)
  - `delete`: 204 / 403 (`AccessDeniedException`, ADMIN-only per
    `@PreAuthorize`) / 404
- **`ChatController`** — `@Tag(name = "Chat")`. Single POST: 200 / 400
  (validation) / 401.
- **`AgentController`** — `@Tag(name = "Agent")`. Same shape as `Chat`.

Every `@ApiResponse` for an error status references the existing
`ErrorResponse` schema (`status`, `error`, `message`, `path`, `details`) so
the generated docs show the real error body instead of a generic one.

### 3. DTO field docs

Add `@Schema(description = ...)` to record components where explanatory
Javadoc already exists, reusing that wording rather than writing new prose:
- `ChatRequest.conversationId` ("omit to start a new conversation; pass back
  the value from a previous ChatResponse to continue one with memory of
  prior turns")
- `DocumentResponse.status` (processing status: PENDING/COMPLETED/FAILED per
  `DocumentIngestionService`)
- Similar treatment for `RegisterRequest`, `LoginRequest`, `AuthResponse`,
  `SourceReference`, `ErrorResponse` fields that already have Javadoc.

No DTO gets *new* explanatory content invented for this task — only existing
Javadoc gets surfaced into `@Schema`.

### Out of scope

- No changes to `application.yml` (springdoc's default paths already match
  what `SecurityConfig` permits).
- No new dependencies.
- No `SecurityConfig` changes.
- No example payloads (`@ExampleObject`) — request/response shapes and
  status codes are enough; examples would need upkeep as DTOs evolve and
  aren't needed for a docs-only pass.

## Testing

Manual verification only (no new business logic to unit test):
1. `./mvnw spring-boot:run`, hit `/swagger-ui/index.html`, confirm all four
   tags appear with their operations.
2. Confirm `/auth/**` operations show no padlock; everything else does.
3. Register + login via the UI, click "Authorize", paste the JWT, then
   exercise a protected endpoint (e.g. `GET /documents`) from the UI to
   confirm the bearer token is actually sent and accepted.
4. Trigger one documented error case (e.g. `GET /documents/{random-uuid}`)
   and confirm the response matches the documented `ErrorResponse` shape and
   status.
