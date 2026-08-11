# Swagger/OpenAPI Documentation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Finish wiring up the `springdoc-openapi-starter-webmvc-ui` dependency (already in `pom.xml`, already permitted by `SecurityConfig`) so Swagger UI is fully usable: JWT bearer auth via the "Authorize" button, and every endpoint documented with real request/response/error detail.

**Architecture:** One new `@Configuration` class (`OpenApiConfig`) provides global `Info` + a `bearerAuth` `SecurityScheme` wired as the default security requirement. Every controller gets a `@Tag` plus per-method `@Operation`/`@ApiResponse` annotations that reference the real DTOs (including `ErrorResponse` for every documented failure mode, matching `GlobalExceptionHandler`'s actual behavior). A handful of DTO record components get `@Schema(description = ...)` reusing wording already present in their Javadoc — no new explanatory content is invented anywhere in this plan.

**Tech Stack:** Spring Boot 4 / Spring MVC, `springdoc-openapi-starter-webmvc-ui` 2.6.0 (already present — brings `io.swagger.v3.oas.annotations.*` and `io.swagger.v3.oas.models.*` transitively, no new dependency needed).

## Global Constraints

- No new dependencies — `springdoc-openapi-starter-webmvc-ui:2.6.0` already provides everything needed (`pom.xml:156-160`).
- No `SecurityConfig` changes — `/swagger-ui/**` and `/v3/api-docs/**` are already permitted (`SecurityConfig.java:69`).
- No `application.yml` changes — springdoc's default paths already match what's permitted.
- No example payloads (`@ExampleObject`) — out of scope per the design doc.
- Every error `@ApiResponse` must reference the real `ErrorResponse` schema, not a generic body.
- DTO `@Schema` descriptions may only reuse wording that already exists in that field's Javadoc — never invent new explanatory text.
- Version string in `OpenApiConfig` is the literal `"0.0.1-SNAPSHOT"` (matches `pom.xml:13`), not a build-injected property — no `build-info` plugin is configured and none should be added for this.

---

### Task 1: `OpenApiConfig` + `ErrorResponse` schema docs (shared groundwork)

This is the foundation every later task's `@ApiResponse` will point at, so it goes first. No dependencies on other tasks.

**Files:**
- Create: `src/main/java/com/rensilver/ai_knowledge_assistant/config/OpenApiConfig.java`
- Modify: `src/main/java/com/rensilver/ai_knowledge_assistant/dto/ErrorResponse.java`

**Interfaces:**
- Produces: a `bearerAuth` security scheme name (string constant `"bearerAuth"`) that every later task's `@SecurityRequirement`/global-default reference relies on implicitly (it's applied globally, so later tasks don't need to name it directly — only `AuthController` needs to override it, via `@SecurityRequirements`, see Task 2).
- Produces: `ErrorResponse` with `@Schema` descriptions — later tasks reference `ErrorResponse.class` in `@Content(schema = @Schema(implementation = ErrorResponse.class))`, no new methods/fields.

- [ ] **Step 1: Create `OpenApiConfig`**

```java
package com.rensilver.ai_knowledge_assistant.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Global OpenAPI metadata and the JWT bearer security scheme used by every
 * protected endpoint. Individual public endpoints (auth register/login)
 * override this default via {@code @SecurityRequirements} on the controller
 * method — see {@code AuthController}.
 */
@Configuration
public class OpenApiConfig {

    public static final String BEARER_SCHEME_NAME = "bearerAuth";

    @Bean
    public OpenAPI openApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("AI Knowledge Assistant API")
                        .description("Corporate RAG chat assistant: document upload/indexing, "
                                + "grounded chat, and tool-calling agent endpoints.")
                        .version("0.0.1-SNAPSHOT"))
                .components(new Components()
                        .addSecuritySchemes(BEARER_SCHEME_NAME, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME_NAME));
    }
}
```

- [ ] **Step 2: Add `@Schema` descriptions to `ErrorResponse`**

Modify `src/main/java/com/rensilver/ai_knowledge_assistant/dto/ErrorResponse.java` — add the import and annotate the four fields whose Javadoc already describes them (`timestamp` has no existing Javadoc, so it's left alone):

```java
package com.rensilver.ai_knowledge_assistant.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

/**
 * Uniform error body returned by {@code GlobalExceptionHandler} for every
 * failed request, so the frontend can rely on a single shape regardless of
 * which endpoint or exception produced it.
 *
 * @param status  HTTP status code, e.g. 409
 * @param error   HTTP reason phrase, e.g. "Conflict"
 * @param message human-readable summary of what went wrong
 * @param path    request URI that triggered the error
 * @param details field-level validation messages, if any (empty otherwise)
 */
public record ErrorResponse(
        Instant timestamp,
        @Schema(description = "HTTP status code, e.g. 409")
        int status,
        @Schema(description = "HTTP reason phrase, e.g. \"Conflict\"")
        String error,
        @Schema(description = "human-readable summary of what went wrong")
        String message,
        @Schema(description = "request URI that triggered the error")
        String path,
        @Schema(description = "field-level validation messages, if any (empty otherwise)")
        List<String> details
) {
    public ErrorResponse(int status, String error, String message, String path, List<String> details) {
        this(Instant.now(), status, error, message, path, details);
    }
}
```

- [ ] **Step 3: Compile to verify no errors**

Run: `./mvnw compile -q`
Expected: build succeeds with no output (annotation-only change, no new dependency needed since springdoc brings `io.swagger.v3.oas.*` transitively).

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/rensilver/ai_knowledge_assistant/config/OpenApiConfig.java \
        src/main/java/com/rensilver/ai_knowledge_assistant/dto/ErrorResponse.java
git commit -m "Add OpenAPI config with JWT bearer scheme and document ErrorResponse"
```

---

### Task 2: `AuthController` + its DTOs

**Depends on:** Task 1 (`ErrorResponse` schema, `bearerAuth` global default).

**Files:**
- Modify: `src/main/java/com/rensilver/ai_knowledge_assistant/controller/AuthController.java`
- Modify: `src/main/java/com/rensilver/ai_knowledge_assistant/dto/AuthResponse.java`

**Interfaces:**
- Consumes: `ErrorResponse.class` (Task 1) for error response schemas.
- Produces: nothing consumed by later tasks — `Auth` tag and its docs are self-contained.

- [ ] **Step 1: Annotate `AuthController`**

Replace the full file content of `src/main/java/com/rensilver/ai_knowledge_assistant/controller/AuthController.java`:

```java
package com.rensilver.ai_knowledge_assistant.controller;

import com.rensilver.ai_knowledge_assistant.dto.AuthResponse;
import com.rensilver.ai_knowledge_assistant.dto.ErrorResponse;
import com.rensilver.ai_knowledge_assistant.dto.LoginRequest;
import com.rensilver.ai_knowledge_assistant.dto.RegisterRequest;
import com.rensilver.ai_knowledge_assistant.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public authentication endpoints (permitted in {@code SecurityConfig} via
 * {@code /auth/**}). Delegates all logic to {@link AuthService}.
 */
@Tag(name = "Auth", description = "Registration and login. Public endpoints — no bearer token required.")
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(summary = "Register a new user",
            description = "Creates a user account and returns a JWT, same shape as login.")
    @SecurityRequirements
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "User created",
                    content = @Content(schema = @Schema(implementation = AuthResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation failed",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Email already in use",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Log in with email and password",
            description = "Returns a JWT to send as `Authorization: Bearer <token>` on subsequent requests.")
    @SecurityRequirements
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Login succeeded",
                    content = @Content(schema = @Schema(implementation = AuthResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation failed",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Bad credentials",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }
}
```

Note on `@SecurityRequirements` (plural, no arguments): this is the springdoc-documented way to declare "no security" on one operation, overriding the global default set in `OpenApiConfig`. Setting `@Operation(security = {})` would **not** work — an empty array there is indistinguishable from "not specified," so the global requirement would still apply and the padlock would stay on. `@SecurityRequirements` with zero `@SecurityRequirement` values is the actual override.

`RegisterRequest` and `LoginRequest` are deliberately **not** touched in this task: both only have class-level Javadoc ("Payload for POST /auth/register" / "/login"), no per-field Javadoc to reuse, so per the "no invented content" constraint there's nothing to add `@Schema` for.

- [ ] **Step 2: Add `@Schema` descriptions to `AuthResponse`**

Modify `src/main/java/com/rensilver/ai_knowledge_assistant/dto/AuthResponse.java`. `role` has an empty `@param role` (no description text in the existing Javadoc), so it's left unannotated rather than inventing wording for it:

```java
package com.rensilver.ai_knowledge_assistant.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Returned by both POST /auth/register and POST /auth/login on success.
 *
 * @param token      signed JWT to send as {@code Authorization: Bearer <token>}
 * @param expiresIn  token lifetime in milliseconds (mirrors app.jwt.expiration-ms),
 *                   so the frontend can proactively refresh/redirect to login
 * @param name       convenience fields so the frontend doesn't need a second
 * @param email      call just to render "logged in as ..."
 * @param role
 */
public record AuthResponse(
        @Schema(description = "signed JWT to send as `Authorization: Bearer <token>`")
        String token,
        @Schema(description = "token lifetime in milliseconds (mirrors app.jwt.expiration-ms), "
                + "so the frontend can proactively refresh/redirect to login")
        long expiresIn,
        @Schema(description = "convenience field so the frontend doesn't need a second call just to render \"logged in as ...\"")
        String name,
        @Schema(description = "convenience field so the frontend doesn't need a second call just to render \"logged in as ...\"")
        String email,
        String role
) {
}
```

- [ ] **Step 3: Compile to verify no errors**

Run: `./mvnw compile -q`
Expected: build succeeds with no output.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/rensilver/ai_knowledge_assistant/controller/AuthController.java \
        src/main/java/com/rensilver/ai_knowledge_assistant/dto/AuthResponse.java
git commit -m "Document Auth endpoints in OpenAPI"
```

---

### Task 3: `DocumentController` + `DocumentResponse`

**Depends on:** Task 1 (`ErrorResponse` schema).

**Files:**
- Modify: `src/main/java/com/rensilver/ai_knowledge_assistant/controller/DocumentController.java`
- Modify: `src/main/java/com/rensilver/ai_knowledge_assistant/dto/DocumentResponse.java`

**Interfaces:**
- Consumes: `ErrorResponse.class` (Task 1).
- Produces: nothing consumed by later tasks.

- [ ] **Step 1: Annotate `DocumentController`**

Replace the full file content of `src/main/java/com/rensilver/ai_knowledge_assistant/controller/DocumentController.java`:

```java
package com.rensilver.ai_knowledge_assistant.controller;

import com.rensilver.ai_knowledge_assistant.document.DocumentService;
import com.rensilver.ai_knowledge_assistant.dto.DocumentResponse;
import com.rensilver.ai_knowledge_assistant.dto.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@Tag(name = "Documents", description = "Upload, list, retrieve, and delete knowledge-base documents.")
@RestController
@RequestMapping("/documents")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @Operation(summary = "Upload a document for ingestion",
            description = "Stores the file and returns immediately with status PROCESSING; parsing, "
                    + "chunking, embedding, and indexing happen asynchronously in the background. "
                    + "Poll GET /documents/{id} for status.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Document stored, ingestion started",
                    content = @Content(schema = @Schema(implementation = DocumentResponse.class))),
            @ApiResponse(responseCode = "400", description = "Unsupported file type",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "413", description = "File exceeds the maximum upload size",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "Document could not be processed",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping(value = "/upload", consumes = "multipart/form-data")
    public ResponseEntity<DocumentResponse> upload(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal UserDetails principal
    ) {
        DocumentResponse response = documentService.upload(file, principal.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "List all documents")
    @ApiResponse(responseCode = "200", description = "Documents retrieved",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = DocumentResponse.class))))
    @GetMapping
    public ResponseEntity<List<DocumentResponse>> list() {
        return ResponseEntity.ok(documentService.list());
    }

    @Operation(summary = "Get a document by id")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Document found",
                    content = @Content(schema = @Schema(implementation = DocumentResponse.class))),
            @ApiResponse(responseCode = "404", description = "No document with that id",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{id}")
    public ResponseEntity<DocumentResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(documentService.get(id));
    }

    @Operation(summary = "Delete a document", description = "Requires the ADMIN role.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Document deleted"),
            @ApiResponse(responseCode = "403", description = "Caller does not have the ADMIN role",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "No document with that id",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        documentService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
```

- [ ] **Step 2: Add a `@Schema` description to `DocumentResponse.status`**

Modify `src/main/java/com/rensilver/ai_knowledge_assistant/dto/DocumentResponse.java`. The description reuses `DocumentStatus`'s own Javadoc (`entity/DocumentStatus.java:4-6`) rather than inventing new wording; no other field has existing per-field Javadoc to draw from, so no other field is annotated:

```java
package com.rensilver.ai_knowledge_assistant.dto;

import com.rensilver.ai_knowledge_assistant.entity.DocumentEntity;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

/**
 * Returned by {@code POST /documents/upload} and {@code GET /documents}.
 */
public record DocumentResponse(
        UUID id,
        String filename,
        String contentType,
        long sizeBytes,
        @Schema(description = "Lifecycle of the document as it moves through the RAG ingestion "
                + "pipeline (parse -> chunk -> embed -> index): PROCESSING, COMPLETED, or FAILED.")
        String status,
        String uploadedBy,
        Instant createdAt
) {
    public static DocumentResponse from(DocumentEntity entity) {
        return new DocumentResponse(
                entity.getId(),
                entity.getFilename(),
                entity.getContentType(),
                entity.getSizeBytes(),
                entity.getStatus().name(),
                entity.getUploadedBy().getEmail(),
                entity.getCreatedAt()
        );
    }
}
```

- [ ] **Step 3: Compile to verify no errors**

Run: `./mvnw compile -q`
Expected: build succeeds with no output.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/rensilver/ai_knowledge_assistant/controller/DocumentController.java \
        src/main/java/com/rensilver/ai_knowledge_assistant/dto/DocumentResponse.java
git commit -m "Document Documents endpoints in OpenAPI"
```

---

### Task 4: `ChatController` + `AgentController` + their DTOs

**Depends on:** Task 1 (`ErrorResponse` schema).

**Files:**
- Modify: `src/main/java/com/rensilver/ai_knowledge_assistant/controller/ChatController.java`
- Modify: `src/main/java/com/rensilver/ai_knowledge_assistant/controller/AgentController.java`
- Modify: `src/main/java/com/rensilver/ai_knowledge_assistant/dto/ChatRequest.java`
- Modify: `src/main/java/com/rensilver/ai_knowledge_assistant/dto/ChatResponse.java`
- Modify: `src/main/java/com/rensilver/ai_knowledge_assistant/dto/SourceReference.java`

**Interfaces:**
- Consumes: `ErrorResponse.class` (Task 1).
- Produces: nothing consumed by later tasks.

- [ ] **Step 1: Annotate `ChatController`**

Replace the full file content of `src/main/java/com/rensilver/ai_knowledge_assistant/controller/ChatController.java`:

```java
package com.rensilver.ai_knowledge_assistant.controller;

import com.rensilver.ai_knowledge_assistant.chat.ChatService;
import com.rensilver.ai_knowledge_assistant.dto.ChatRequest;
import com.rensilver.ai_knowledge_assistant.dto.ChatResponse;
import com.rensilver.ai_knowledge_assistant.dto.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Chat", description = "Grounded chat over the indexed knowledge base.")
@RestController
@RequestMapping("/chat")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @Operation(summary = "Ask a question grounded in the knowledge base",
            description = "Retrieves relevant document chunks, re-ranks them, and answers with "
                    + "citations. Pass conversationId back from a prior response to continue that "
                    + "conversation with memory of earlier turns.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Answer produced",
                    content = @Content(schema = @Schema(implementation = ChatResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation failed",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping
    public ResponseEntity<ChatResponse> chat(
            @Valid @RequestBody ChatRequest request,
            @AuthenticationPrincipal UserDetails principal
    ) {
        return ResponseEntity.ok(chatService.chat(request, principal.getUsername()));
    }
}
```

- [ ] **Step 2: Annotate `AgentController`**

Replace the full file content of `src/main/java/com/rensilver/ai_knowledge_assistant/controller/AgentController.java`:

```java
package com.rensilver.ai_knowledge_assistant.controller;

import com.rensilver.ai_knowledge_assistant.agent.AgentService;
import com.rensilver.ai_knowledge_assistant.dto.ChatRequest;
import com.rensilver.ai_knowledge_assistant.dto.ChatResponse;
import com.rensilver.ai_knowledge_assistant.dto.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * V4 tool-calling endpoint. Same request/response shape as {@code /chat}, but
 * the model chooses when to search the knowledge base rather than always being
 * handed one set of retrieved chunks — see {@code AgentService}.
 */
@Tag(name = "Agent", description = "Tool-calling agent: the model chooses when to search the "
        + "knowledge base or external knowledge rather than always being handed retrieved context.")
@RestController
@RequestMapping("/agent")
public class AgentController {

    private final AgentService agentService;

    public AgentController(AgentService agentService) {
        this.agentService = agentService;
    }

    @Operation(summary = "Ask a question via the tool-calling agent",
            description = "Same request/response shape as POST /chat, but the model decides "
                    + "whether to call searchDocuments/listDocuments/searchExternalKnowledge rather "
                    + "than always being handed one fixed set of retrieved chunks.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Answer produced",
                    content = @Content(schema = @Schema(implementation = ChatResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation failed",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping
    public ResponseEntity<ChatResponse> chat(
            @Valid @RequestBody ChatRequest request,
            @AuthenticationPrincipal UserDetails principal
    ) {
        return ResponseEntity.ok(agentService.chat(request, principal.getUsername()));
    }
}
```

- [ ] **Step 3: Add `@Schema` descriptions to `ChatRequest`**

Modify `src/main/java/com/rensilver/ai_knowledge_assistant/dto/ChatRequest.java`, reusing its existing per-param Javadoc for both fields:

```java
package com.rensilver.ai_knowledge_assistant.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

/**
 * Payload for POST /chat.
 *
 * @param message        the user's question
 * @param conversationId omit to start a new conversation; pass back the
 *                       value from a previous {@link ChatResponse} to
 *                       continue one with memory of prior turns
 */
public record ChatRequest(

        @NotBlank(message = "Message is required")
        @Schema(description = "the user's question")
        String message,

        @Schema(description = "omit to start a new conversation; pass back the value from a "
                + "previous ChatResponse to continue one with memory of prior turns")
        UUID conversationId
) {
}
```

- [ ] **Step 4: Add `@Schema` descriptions to `ChatResponse`**

Modify `src/main/java/com/rensilver/ai_knowledge_assistant/dto/ChatResponse.java`, reusing its existing per-param Javadoc for all three fields:

```java
package com.rensilver.ai_knowledge_assistant.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

/**
 * Returned by POST /chat.
 *
 * @param answer         the grounded answer produced by the LLM
 * @param conversationId echo this back on the next request to continue the
 *                       same conversation with memory of this turn
 * @param sources        document pages the answer was grounded in (empty if
 *                       no relevant context was found)
 */
public record ChatResponse(
        @Schema(description = "the grounded answer produced by the LLM")
        String answer,
        @Schema(description = "echo this back on the next request to continue the same "
                + "conversation with memory of this turn")
        UUID conversationId,
        @Schema(description = "document pages the answer was grounded in (empty if no relevant "
                + "context was found)")
        List<SourceReference> sources
) {
}
```

- [ ] **Step 5: Add `@Schema` descriptions to `SourceReference`**

Modify `src/main/java/com/rensilver/ai_knowledge_assistant/dto/SourceReference.java`, reusing its existing per-param Javadoc for both record components (only the header/import and record declaration line change; the rest of the class — `UNKNOWN_FILENAME`, `from`, `readPage`, `describe` — is unchanged):

```java
package com.rensilver.ai_knowledge_assistant.dto;

import com.rensilver.ai_knowledge_assistant.rag.DocumentChunker;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.ai.document.Document;

/**
 * One document location an answer was grounded in, e.g.
 * {@code AWS Architecture Guide.pdf}, page 12.
 *
 * @param filename original name of the uploaded document
 * @param page     1-based page the cited chunk came from, or {@code null} for
 *                 documents indexed before page tracking existed
 */
public record SourceReference(
        @Schema(description = "original name of the uploaded document")
        String filename,
        @Schema(description = "1-based page the cited chunk came from, or null for documents "
                + "indexed before page tracking existed")
        Integer page
) {

    private static final String UNKNOWN_FILENAME = "unknown source";

    public static SourceReference from(Document chunk) {
        Object filename = chunk.getMetadata().get(DocumentChunker.FILENAME);
        return new SourceReference(
                filename != null ? filename.toString() : UNKNOWN_FILENAME,
                readPage(chunk)
        );
    }

    /**
     * pgvector round-trips metadata through JSON, so a page stored as an
     * {@code Integer} can come back as any {@link Number} subtype.
     */
    private static Integer readPage(Document chunk) {
        Object page = chunk.getMetadata().get(DocumentChunker.PAGE);
        return page instanceof Number number ? number.intValue() : null;
    }

    /** Rendered into prompts and readable by a human: {@code guide.pdf (page 12)}. */
    public String describe() {
        return page != null ? "%s (page %d)".formatted(filename, page) : filename;
    }
}
```

- [ ] **Step 6: Compile to verify no errors**

Run: `./mvnw compile -q`
Expected: build succeeds with no output.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/rensilver/ai_knowledge_assistant/controller/ChatController.java \
        src/main/java/com/rensilver/ai_knowledge_assistant/controller/AgentController.java \
        src/main/java/com/rensilver/ai_knowledge_assistant/dto/ChatRequest.java \
        src/main/java/com/rensilver/ai_knowledge_assistant/dto/ChatResponse.java \
        src/main/java/com/rensilver/ai_knowledge_assistant/dto/SourceReference.java
git commit -m "Document Chat and Agent endpoints in OpenAPI"
```

---

### Task 5: Manual verification

**Depends on:** Tasks 1-4 (every controller and DTO annotated).

**Files:** none (verification only).

**Interfaces:** none.

- [ ] **Step 1: Start dependencies**

Run: `docker compose up -d`
Expected: postgres+pgvector and ollama containers start (the one-shot model pull job runs and exits 0).

- [ ] **Step 2: Start the app**

Run: `./mvnw spring-boot:run`
Expected: app starts on `:8080` with no errors; Flyway applies V1-V5 (already-applied migrations are no-ops if the DB volume persists from prior runs).

- [ ] **Step 3: Open Swagger UI and confirm structure**

Open `http://localhost:8080/swagger-ui/index.html` in a browser.
Expected: four tag groups — **Auth**, **Documents**, **Chat**, **Agent** — each with the operations defined above (register/login; upload/list/get/delete; chat; agent chat). Expand a couple of operations and confirm the response schemas match `AuthResponse`/`DocumentResponse`/`ChatResponse`/`ErrorResponse` (not generic/empty schemas).

- [ ] **Step 4: Confirm padlock visibility matches security**

Expected: `POST /auth/register` and `POST /auth/login` show **no** padlock icon. Every other operation (`Documents`, `Chat`, `Agent`) shows a padlock.

- [ ] **Step 5: Exercise the full auth + protected-call flow through the UI**

1. Expand `POST /auth/register`, click "Try it out", submit a registration payload (unique email, password ≥ 8 chars).
2. Confirm the response is `201` with a `token` field in the body.
3. Click the top-right "Authorize" button, paste the token (no `Bearer ` prefix needed — springdoc adds it), click "Authorize" then "Close".
4. Expand `GET /documents`, click "Try it out", "Execute".
Expected: `200` with a JSON array (empty or populated) — confirms the bearer token was sent and accepted by `JwtFilter`/`SecurityConfig`.

- [ ] **Step 6: Confirm a documented error case matches the real response**

Expand `GET /documents/{id}`, "Try it out", pass a random UUID (e.g. `00000000-0000-0000-0000-000000000000`), "Execute".
Expected: `404` response whose body matches the documented `ErrorResponse` shape — `status`, `error`, `message`, `path`, `details` fields all present, `status: 404`.

- [ ] **Step 7: Stop the app**

Stop the `./mvnw spring-boot:run` process (Ctrl-C). No commit for this task — it's verification only, no files changed.
