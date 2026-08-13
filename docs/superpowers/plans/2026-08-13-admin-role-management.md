# Admin Bootstrap + Role Promotion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let the application grant the `ADMIN` role entirely through its own API — one bootstrap admin created from environment variables at startup, plus an admin-only endpoint to promote/demote any other user — so nobody ever needs direct database access to manage roles once the backend and frontend are on separate cloud providers.

**Architecture:** Follows the project's existing controller → service → repository layering exactly (see `DocumentController` → `DocumentService` → `DocumentRepository` for the reference shape). Adds one new `ApplicationRunner` bean for the one-time bootstrap, one new controller/service pair for ongoing role management, and reuses `GlobalExceptionHandler`'s existing exception-to-`ErrorResponse` translation pattern.

**Tech Stack:** Java 21, Spring Boot 4 (Spring MVC, Spring Data JPA, Spring Security method security), Spring AI unaffected, JUnit 5 + Mockito + AssertJ (unit), `@WebMvcTest` (controller slice), Testcontainers `pgvector/pgvector:pg16` (integration).

## Global Constraints

- Docker Postgres for tests must be `pgvector/pgvector`, never plain `postgres` — see `PgVectorTestSupport`.
- No hardcoded credentials in source: the bootstrap admin's email/password come only from `ADMIN_BOOTSTRAP_EMAIL` / `ADMIN_BOOTSTRAP_PASSWORD`, with an empty-string default so the feature is a no-op unless explicitly configured (matches the spec's decision, `docs/superpowers/specs/2026-08-13-admin-role-management-design.md`).
- Self-demotion is **not** specially blocked — only the last-remaining-admin guard applies.
- `@PreAuthorize` is inert under `@WebMvcTest` in this project (`SecurityConfig`, which carries `@EnableMethodSecurity`, isn't part of that test slice's context). Authorization enforcement (403s) is verified only in the full-context integration test, never in a `@WebMvcTest` slice.
- Follow existing package-by-layer placement: DTOs in `dto/`, exceptions in `exception/`, controllers in `controller/`, services in `service/`, the bootstrap runner in `config/`.

---

### Task 1: DTOs, exceptions, and repository support

**Files:**
- Create: `src/main/java/com/rensilver/ai_knowledge_assistant/dto/UserResponse.java`
- Create: `src/main/java/com/rensilver/ai_knowledge_assistant/dto/RoleUpdateRequest.java`
- Create: `src/main/java/com/rensilver/ai_knowledge_assistant/exception/UserNotFoundException.java`
- Create: `src/main/java/com/rensilver/ai_knowledge_assistant/exception/LastAdminException.java`
- Modify: `src/main/java/com/rensilver/ai_knowledge_assistant/exception/GlobalExceptionHandler.java`
- Modify: `src/main/java/com/rensilver/ai_knowledge_assistant/repository/UserRepository.java`

**Interfaces:**
- Produces: `UserResponse.from(UserEntity): UserResponse` — record `(UUID id, String name, String email, String role, Instant createdAt)`.
- Produces: `RoleUpdateRequest` — record `(@NotNull Role role)`.
- Produces: `UserNotFoundException(UUID id)`, `LastAdminException()` — both `RuntimeException`, mapped by `GlobalExceptionHandler` to `404` and `409` respectively.
- Produces: `UserRepository.countByRole(Role role): long`.

This task has no independent business logic to unit-test (records and exception classes carry no behavior) — the deliverable is verified by compiling and by Task 2/3's tests, which depend on these types existing.

- [ ] **Step 1: Create `UserResponse`**

```java
package com.rensilver.ai_knowledge_assistant.dto;

import com.rensilver.ai_knowledge_assistant.entity.UserEntity;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

/**
 * Returned by {@code GET /users} and {@code PATCH /users/{id}/role}.
 */
public record UserResponse(
        UUID id,
        String name,
        String email,
        @Schema(description = "ADMIN or USER")
        String role,
        Instant createdAt
) {
    public static UserResponse from(UserEntity entity) {
        return new UserResponse(
                entity.getId(),
                entity.getName(),
                entity.getEmail(),
                entity.getRole().name(),
                entity.getCreatedAt()
        );
    }
}
```

- [ ] **Step 2: Create `RoleUpdateRequest`**

```java
package com.rensilver.ai_knowledge_assistant.dto;

import com.rensilver.ai_knowledge_assistant.entity.Role;
import jakarta.validation.constraints.NotNull;

/**
 * Payload for PATCH /users/{id}/role.
 */
public record RoleUpdateRequest(
        @NotNull(message = "Role is required")
        Role role
) {
}
```

- [ ] **Step 3: Create `UserNotFoundException`**

```java
package com.rensilver.ai_knowledge_assistant.exception;

import java.util.UUID;

public class UserNotFoundException extends RuntimeException {
    public UserNotFoundException(UUID id) {
        super("No user found with id: " + id);
    }
}
```

- [ ] **Step 4: Create `LastAdminException`**

```java
package com.rensilver.ai_knowledge_assistant.exception;

/**
 * Thrown by UserService when a role change would leave zero ADMIN users.
 * Mapped to HTTP 409 by {@link GlobalExceptionHandler}.
 */
public class LastAdminException extends RuntimeException {
    public LastAdminException() {
        super("Cannot change role: this is the last remaining admin");
    }
}
```

- [ ] **Step 5: Wire both new exceptions into `GlobalExceptionHandler`**

Add these two handler methods, placed next to `handleDocumentNotFound` (same 404 shape) and `handleEmailAlreadyInUse` (same 409 shape):

```java
    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleUserNotFound(
            UserNotFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), request, List.of());
    }

    @ExceptionHandler(LastAdminException.class)
    public ResponseEntity<ErrorResponse> handleLastAdmin(
            LastAdminException ex, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), request, List.of());
    }
```

Add the import: `import com.rensilver.ai_knowledge_assistant.exception.UserNotFoundException;` — actually both exceptions already live in the same `exception` package as `GlobalExceptionHandler`, so no new import is needed (same as `DocumentNotFoundException` today).

- [ ] **Step 6: Add `countByRole` to `UserRepository`**

Add this method to the interface, with the corresponding import:

```java
import com.rensilver.ai_knowledge_assistant.entity.Role;
```

```java
    /**
     * Backs the last-admin guard in UserService.changeRole: a demotion from
     * ADMIN to USER is rejected if this returns 1 for the demoted user.
     */
    long countByRole(Role role);
```

- [ ] **Step 7: Compile**

Run: `./mvnw compile`
Expected: `BUILD SUCCESS`

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/rensilver/ai_knowledge_assistant/dto/UserResponse.java \
        src/main/java/com/rensilver/ai_knowledge_assistant/dto/RoleUpdateRequest.java \
        src/main/java/com/rensilver/ai_knowledge_assistant/exception/UserNotFoundException.java \
        src/main/java/com/rensilver/ai_knowledge_assistant/exception/LastAdminException.java \
        src/main/java/com/rensilver/ai_knowledge_assistant/exception/GlobalExceptionHandler.java \
        src/main/java/com/rensilver/ai_knowledge_assistant/repository/UserRepository.java
git commit -m "Add DTOs, exceptions, and repository support for role management"
```

---

### Task 2: `UserService`

**Files:**
- Create: `src/main/java/com/rensilver/ai_knowledge_assistant/service/UserService.java`
- Test: `src/test/java/com/rensilver/ai_knowledge_assistant/service/UserServiceTest.java`

**Interfaces:**
- Consumes: `UserRepository.findAll()`, `.findById(UUID)`, `.countByRole(Role)`, `.save(UserEntity)` (all pre-existing except `countByRole`, added in Task 1). `UserResponse.from(UserEntity)`, `UserNotFoundException(UUID)`, `LastAdminException()` (Task 1).
- Produces: `UserService.list(): List<UserResponse>`, `UserService.changeRole(UUID id, Role newRole, String actorEmail): UserResponse` — both consumed by `UserController` in Task 3.

- [ ] **Step 1: Write the failing tests**

Create `src/test/java/com/rensilver/ai_knowledge_assistant/service/UserServiceTest.java`:

```java
package com.rensilver.ai_knowledge_assistant.service;

import com.rensilver.ai_knowledge_assistant.dto.UserResponse;
import com.rensilver.ai_knowledge_assistant.entity.Role;
import com.rensilver.ai_knowledge_assistant.entity.UserEntity;
import com.rensilver.ai_knowledge_assistant.exception.LastAdminException;
import com.rensilver.ai_knowledge_assistant.exception.UserNotFoundException;
import com.rensilver.ai_knowledge_assistant.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository);
    }

    private UserEntity user(UUID id, Role role) {
        return UserEntity.builder()
                .id(id)
                .name("Ada Lovelace")
                .email("ada@example.com")
                .password("bcrypt-hash")
                .role(role)
                .createdAt(Instant.now())
                .build();
    }

    @Test
    void changeRoleThrowsWhenUserDoesNotExist() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.changeRole(id, Role.ADMIN, "admin@example.com"))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void changeRoleIsANoOpWhenRoleIsUnchanged() {
        UUID id = UUID.randomUUID();
        UserEntity existing = user(id, Role.USER);
        when(userRepository.findById(id)).thenReturn(Optional.of(existing));

        UserResponse response = userService.changeRole(id, Role.USER, "admin@example.com");

        assertThat(response.role()).isEqualTo("USER");
        verify(userRepository, never()).save(any());
        verify(userRepository, never()).countByRole(any());
    }

    @Test
    void changeRoleRejectsDemotingTheLastRemainingAdmin() {
        UUID id = UUID.randomUUID();
        UserEntity lastAdmin = user(id, Role.ADMIN);
        when(userRepository.findById(id)).thenReturn(Optional.of(lastAdmin));
        when(userRepository.countByRole(Role.ADMIN)).thenReturn(1L);

        assertThatThrownBy(() -> userService.changeRole(id, Role.USER, "admin@example.com"))
                .isInstanceOf(LastAdminException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void changeRoleDemotesAnAdminWhenAnotherAdminRemains() {
        UUID id = UUID.randomUUID();
        UserEntity admin = user(id, Role.ADMIN);
        when(userRepository.findById(id)).thenReturn(Optional.of(admin));
        when(userRepository.countByRole(Role.ADMIN)).thenReturn(2L);

        UserResponse response = userService.changeRole(id, Role.USER, "admin@example.com");

        assertThat(response.role()).isEqualTo("USER");
        assertThat(admin.getRole()).isEqualTo(Role.USER);
        verify(userRepository).save(admin);
    }

    @Test
    void changeRolePromotesAUserToAdminWithoutCheckingAdminCount() {
        UUID id = UUID.randomUUID();
        UserEntity plainUser = user(id, Role.USER);
        when(userRepository.findById(id)).thenReturn(Optional.of(plainUser));

        UserResponse response = userService.changeRole(id, Role.ADMIN, "admin@example.com");

        assertThat(response.role()).isEqualTo("ADMIN");
        verify(userRepository).save(plainUser);
        verify(userRepository, never()).countByRole(any());
    }

    @Test
    void listReturnsAllUsersMappedToResponses() {
        UserEntity a = user(UUID.randomUUID(), Role.ADMIN);
        UserEntity b = user(UUID.randomUUID(), Role.USER);
        when(userRepository.findAll()).thenReturn(List.of(a, b));

        List<UserResponse> result = userService.list();

        assertThat(result).hasSize(2);
        assertThat(result).extracting(UserResponse::role).containsExactlyInAnyOrder("ADMIN", "USER");
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./mvnw test -Dtest=UserServiceTest`
Expected: compilation failure (`UserService` doesn't exist yet).

- [ ] **Step 3: Implement `UserService`**

Create `src/main/java/com/rensilver/ai_knowledge_assistant/service/UserService.java`:

```java
package com.rensilver.ai_knowledge_assistant.service;

import com.rensilver.ai_knowledge_assistant.dto.UserResponse;
import com.rensilver.ai_knowledge_assistant.entity.Role;
import com.rensilver.ai_knowledge_assistant.entity.UserEntity;
import com.rensilver.ai_knowledge_assistant.exception.LastAdminException;
import com.rensilver.ai_knowledge_assistant.exception.UserNotFoundException;
import com.rensilver.ai_knowledge_assistant.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Backs GET /users and PATCH /users/{id}/role. The only path in the
 * application that ever changes a user's role after registration.
 */
@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public List<UserResponse> list() {
        return userRepository.findAll().stream()
                .map(UserResponse::from)
                .toList();
    }

    @Transactional
    public UserResponse changeRole(UUID id, Role newRole, String actorEmail) {
        UserEntity user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));

        Role currentRole = user.getRole();
        if (currentRole == newRole) {
            return UserResponse.from(user);
        }

        if (currentRole == Role.ADMIN && newRole == Role.USER && userRepository.countByRole(Role.ADMIN) <= 1) {
            throw new LastAdminException();
        }

        user.setRole(newRole);
        userRepository.save(user);

        log.info("Role changed: actor={} userId={} from={} to={}", actorEmail, id, currentRole, newRole);

        return UserResponse.from(user);
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./mvnw test -Dtest=UserServiceTest`
Expected: `BUILD SUCCESS`, 6 tests passing.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/rensilver/ai_knowledge_assistant/service/UserService.java \
        src/test/java/com/rensilver/ai_knowledge_assistant/service/UserServiceTest.java
git commit -m "Add UserService with role-change business rules"
```

---

### Task 3: `UserController` and CORS support for PATCH

**Files:**
- Create: `src/main/java/com/rensilver/ai_knowledge_assistant/controller/UserController.java`
- Modify: `src/main/java/com/rensilver/ai_knowledge_assistant/config/SecurityConfig.java`
- Test: `src/test/java/com/rensilver/ai_knowledge_assistant/controller/UserControllerTest.java`

**Interfaces:**
- Consumes: `UserService.list()`, `UserService.changeRole(UUID, Role, String)` (Task 2). `RoleUpdateRequest`, `UserResponse` (Task 1).
- Produces: `GET /users`, `PATCH /users/{id}/role` HTTP endpoints, consumed by the integration test in Task 5 and, eventually, the frontend.

This is the first `PATCH` endpoint in the app — `SecurityConfig`'s CORS configuration currently only allows `GET, POST, PUT, DELETE, OPTIONS`, which would silently block a cross-origin frontend's `PATCH` request at the preflight stage. Fixing that is part of this task since `UserController` is the first consumer that needs it.

- [ ] **Step 1: Write the failing tests**

Create `src/test/java/com/rensilver/ai_knowledge_assistant/controller/UserControllerTest.java`. Note: per the Global Constraints, this slice test does **not** assert 403/authorization — `@PreAuthorize` is inert under `@WebMvcTest` here since `SecurityConfig` isn't loaded into the slice. Authorization is covered end-to-end in Task 5.

```java
package com.rensilver.ai_knowledge_assistant.controller;

import com.rensilver.ai_knowledge_assistant.dto.UserResponse;
import com.rensilver.ai_knowledge_assistant.exception.LastAdminException;
import com.rensilver.ai_knowledge_assistant.exception.UserNotFoundException;
import com.rensilver.ai_knowledge_assistant.security.JwtService;
import com.rensilver.ai_knowledge_assistant.security.UserDetailsServiceImpl;
import com.rensilver.ai_knowledge_assistant.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Covers request/response mapping and exception translation only.
 * @PreAuthorize enforcement is not testable here — see AdminRoleManagementIT.
 */
@WebMvcTest(UserController.class)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    // JwtFilter is a servlet Filter, so @WebMvcTest picks it up even though
    // its own dependencies fall outside the slice; see DocumentControllerTest.
    @MockitoBean
    private JwtService jwtService;
    @MockitoBean
    private UserDetailsServiceImpl userDetailsService;

    @Test
    @WithMockUser
    void listReturnsAllUsers() throws Exception {
        UserResponse response = new UserResponse(UUID.randomUUID(), "Ada", "ada@example.com", "USER", Instant.now());
        when(userService.list()).thenReturn(List.of(response));

        mockMvc.perform(get("/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].email").value("ada@example.com"));
    }

    @Test
    @WithMockUser(username = "root@example.com")
    void changeRoleReturnsTheUpdatedUser() throws Exception {
        UUID id = UUID.randomUUID();
        UserResponse response = new UserResponse(id, "Ada", "ada@example.com", "ADMIN", Instant.now());
        when(userService.changeRole(eq(id), any(), eq("root@example.com"))).thenReturn(response);

        mockMvc.perform(patch("/users/{id}/role", id)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    @WithMockUser
    void changeRoleReturns404WhenTheUserDoesNotExist() throws Exception {
        UUID id = UUID.randomUUID();
        when(userService.changeRole(eq(id), any(), any())).thenThrow(new UserNotFoundException(id));

        mockMvc.perform(patch("/users/{id}/role", id)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser
    void changeRoleReturns409WhenDemotingTheLastAdmin() throws Exception {
        UUID id = UUID.randomUUID();
        when(userService.changeRole(eq(id), any(), any())).thenThrow(new LastAdminException());

        mockMvc.perform(patch("/users/{id}/role", id)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"USER\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser
    void changeRoleReturns400WhenRoleIsMissing() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(patch("/users/{id}/role", id)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./mvnw test -Dtest=UserControllerTest`
Expected: compilation failure (`UserController` doesn't exist yet).

- [ ] **Step 3: Implement `UserController`**

Create `src/main/java/com/rensilver/ai_knowledge_assistant/controller/UserController.java`:

```java
package com.rensilver.ai_knowledge_assistant.controller;

import com.rensilver.ai_knowledge_assistant.dto.ErrorResponse;
import com.rensilver.ai_knowledge_assistant.dto.RoleUpdateRequest;
import com.rensilver.ai_knowledge_assistant.dto.UserResponse;
import com.rensilver.ai_knowledge_assistant.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Users", description = "Admin-only user listing and role management.")
@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @Operation(summary = "List all users", description = "Requires the ADMIN role.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Users retrieved",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = UserResponse.class)))),
            @ApiResponse(responseCode = "403", description = "Caller does not have the ADMIN role",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token (no response body)")
    })
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<UserResponse>> list() {
        return ResponseEntity.ok(userService.list());
    }

    @Operation(summary = "Change a user's role",
            description = "Requires the ADMIN role. Cannot demote the last remaining admin.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Role updated",
                    content = @Content(schema = @Schema(implementation = UserResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation failed",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Caller does not have the ADMIN role",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "No user with that id",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Would leave zero remaining admins",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token (no response body)")
    })
    @PatchMapping("/{id}/role")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponse> changeRole(
            @PathVariable UUID id,
            @Valid @RequestBody RoleUpdateRequest request,
            @AuthenticationPrincipal UserDetails principal
    ) {
        return ResponseEntity.ok(userService.changeRole(id, request.role(), principal.getUsername()));
    }
}
```

- [ ] **Step 4: Add `PATCH` to the CORS allowed methods**

In `src/main/java/com/rensilver/ai_knowledge_assistant/config/SecurityConfig.java`, change:

```java
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
```

to:

```java
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./mvnw test -Dtest=UserControllerTest`
Expected: `BUILD SUCCESS`, 5 tests passing.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/rensilver/ai_knowledge_assistant/controller/UserController.java \
        src/main/java/com/rensilver/ai_knowledge_assistant/config/SecurityConfig.java \
        src/test/java/com/rensilver/ai_knowledge_assistant/controller/UserControllerTest.java
git commit -m "Add UserController for admin-only user listing and role changes"
```

---

### Task 4: `AdminBootstrapRunner`

**Files:**
- Create: `src/main/java/com/rensilver/ai_knowledge_assistant/config/AdminBootstrapRunner.java`
- Modify: `src/main/resources/application.yml`
- Test: `src/test/java/com/rensilver/ai_knowledge_assistant/config/AdminBootstrapRunnerTest.java`

**Interfaces:**
- Consumes: `UserRepository.existsByEmail(String)`, `.save(UserEntity)` (pre-existing). `PasswordEncoder.encode(String)` (pre-existing bean). `RegisterRequest` (pre-existing, reused purely for its bean-validation constraints). `jakarta.validation.Validator` (Spring Boot auto-configured bean, already used implicitly by `@Valid` elsewhere in the app).
- Produces: on startup, either a new `UserEntity` with `role = ADMIN`, or a documented no-op — consumed by Task 5's integration test, which logs in as this bootstrapped admin.

- [ ] **Step 1: Write the failing tests**

Create `src/test/java/com/rensilver/ai_knowledge_assistant/config/AdminBootstrapRunnerTest.java`:

```java
package com.rensilver.ai_knowledge_assistant.config;

import com.rensilver.ai_knowledge_assistant.entity.Role;
import com.rensilver.ai_knowledge_assistant.entity.UserEntity;
import com.rensilver.ai_knowledge_assistant.repository.UserRepository;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminBootstrapRunnerTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void doesNothingWhenBootstrapEmailIsBlank() throws Exception {
        AdminBootstrapRunner runner =
                new AdminBootstrapRunner("", "irrelevant-but-long-enough", userRepository, passwordEncoder, validator);

        runner.run(null);

        verifyNoInteractions(userRepository, passwordEncoder);
    }

    @Test
    void doesNothingWhenBootstrapPasswordIsBlank() throws Exception {
        AdminBootstrapRunner runner =
                new AdminBootstrapRunner("admin@example.com", "", userRepository, passwordEncoder, validator);

        runner.run(null);

        verifyNoInteractions(userRepository, passwordEncoder);
    }

    @Test
    void doesNothingWhenAUserWithThatEmailAlreadyExists() throws Exception {
        when(userRepository.existsByEmail("admin@example.com")).thenReturn(true);
        AdminBootstrapRunner runner = new AdminBootstrapRunner(
                "admin@example.com", "correct-password", userRepository, passwordEncoder, validator);

        runner.run(null);

        verify(userRepository, never()).save(any());
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void skipsBootstrapWhenTheCredentialsFailValidation() throws Exception {
        when(userRepository.existsByEmail("not-an-email")).thenReturn(false);
        AdminBootstrapRunner runner = new AdminBootstrapRunner(
                "not-an-email", "correct-password", userRepository, passwordEncoder, validator);

        runner.run(null);

        verify(userRepository, never()).save(any());
    }

    @Test
    void createsAnAdminUserWhenCredentialsAreValidAndNoUserExistsYet() throws Exception {
        when(userRepository.existsByEmail("admin@example.com")).thenReturn(false);
        when(passwordEncoder.encode("correct-password")).thenReturn("bcrypt-hash");
        AdminBootstrapRunner runner = new AdminBootstrapRunner(
                "admin@example.com", "correct-password", userRepository, passwordEncoder, validator);

        runner.run(null);

        ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).save(captor.capture());
        UserEntity saved = captor.getValue();
        assertThat(saved.getEmail()).isEqualTo("admin@example.com");
        assertThat(saved.getPassword()).isEqualTo("bcrypt-hash");
        assertThat(saved.getRole()).isEqualTo(Role.ADMIN);
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./mvnw test -Dtest=AdminBootstrapRunnerTest`
Expected: compilation failure (`AdminBootstrapRunner` doesn't exist yet).

- [ ] **Step 3: Implement `AdminBootstrapRunner`**

Create `src/main/java/com/rensilver/ai_knowledge_assistant/config/AdminBootstrapRunner.java`:

```java
package com.rensilver.ai_knowledge_assistant.config;

import com.rensilver.ai_knowledge_assistant.dto.RegisterRequest;
import com.rensilver.ai_knowledge_assistant.entity.Role;
import com.rensilver.ai_knowledge_assistant.entity.UserEntity;
import com.rensilver.ai_knowledge_assistant.repository.UserRepository;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Creates exactly one ADMIN user from ADMIN_BOOTSTRAP_EMAIL /
 * ADMIN_BOOTSTRAP_PASSWORD on startup, so a fresh deployment always has a
 * working admin without anyone touching the database directly. A no-op
 * unless both are set; a no-op again once that email already has a user, so
 * it's safe to leave the env vars in place across restarts.
 *
 * <p>Validated with the same constraints RegisterRequest enforces on
 * self-registration, so a typo'd env var is skipped with a warning instead
 * of crashing the app at boot.
 */
@Component
public class AdminBootstrapRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapRunner.class);

    private final String bootstrapEmail;
    private final String bootstrapPassword;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final Validator validator;

    public AdminBootstrapRunner(
            @Value("${app.admin.bootstrap-email:}") String bootstrapEmail,
            @Value("${app.admin.bootstrap-password:}") String bootstrapPassword,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            Validator validator
    ) {
        this.bootstrapEmail = bootstrapEmail;
        this.bootstrapPassword = bootstrapPassword;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.validator = validator;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (bootstrapEmail.isBlank() || bootstrapPassword.isBlank()) {
            return;
        }

        if (userRepository.existsByEmail(bootstrapEmail)) {
            log.info("Admin bootstrap skipped: a user with email {} already exists", bootstrapEmail);
            return;
        }

        RegisterRequest candidate = new RegisterRequest("Admin", bootstrapEmail, bootstrapPassword);
        Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(candidate);
        if (!violations.isEmpty()) {
            log.warn("Admin bootstrap skipped: ADMIN_BOOTSTRAP_EMAIL/ADMIN_BOOTSTRAP_PASSWORD failed validation ({} issue(s))",
                    violations.size());
            return;
        }

        UserEntity admin = UserEntity.builder()
                .name("Admin")
                .email(bootstrapEmail)
                .password(passwordEncoder.encode(bootstrapPassword))
                .role(Role.ADMIN)
                .build();
        userRepository.save(admin);

        log.info("Bootstrapped initial admin user: {}", bootstrapEmail);
    }
}
```

- [ ] **Step 4: Wire the env vars in `application.yml`**

In `src/main/resources/application.yml`, add a new `admin` block under `app:`, alongside `jwt`, `document`, and `rag`:

```yaml
  admin:
    # Optional: if both are set and no user with this email exists yet, an
    # ADMIN user is created on startup. Leave unset for local dev unless you
    # need one. See AdminBootstrapRunner.
    #   export ADMIN_BOOTSTRAP_EMAIL="admin@example.com"
    #   export ADMIN_BOOTSTRAP_PASSWORD="$(openssl rand -base64 18)"
    bootstrap-email: ${ADMIN_BOOTSTRAP_EMAIL:}
    bootstrap-password: ${ADMIN_BOOTSTRAP_PASSWORD:}
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./mvnw test -Dtest=AdminBootstrapRunnerTest`
Expected: `BUILD SUCCESS`, 5 tests passing.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/rensilver/ai_knowledge_assistant/config/AdminBootstrapRunner.java \
        src/main/resources/application.yml \
        src/test/java/com/rensilver/ai_knowledge_assistant/config/AdminBootstrapRunnerTest.java
git commit -m "Add AdminBootstrapRunner to seed the first ADMIN from env vars"
```

---

### Task 5: End-to-end integration test

**Files:**
- Create: `src/test/java/com/rensilver/ai_knowledge_assistant/controller/AdminRoleManagementIT.java`

**Interfaces:**
- Consumes: everything from Tasks 1–4 — `AdminBootstrapRunner`, `UserController`, `UserService`, `RoleUpdateRequest`, `UserResponse`, plus pre-existing `AuthResponse`, `LoginRequest`, `RegisterRequest`, and `PgVectorTestSupport`.
- Produces: nothing further downstream — this is the final verification that the whole feature works through the real HTTP layer with real `SecurityConfig` method security.

- [ ] **Step 1: Write the test**

Create `src/test/java/com/rensilver/ai_knowledge_assistant/controller/AdminRoleManagementIT.java`:

```java
package com.rensilver.ai_knowledge_assistant.controller;

import com.rensilver.ai_knowledge_assistant.PgVectorTestSupport;
import com.rensilver.ai_knowledge_assistant.dto.AuthResponse;
import com.rensilver.ai_knowledge_assistant.dto.LoginRequest;
import com.rensilver.ai_knowledge_assistant.dto.RegisterRequest;
import com.rensilver.ai_knowledge_assistant.dto.RoleUpdateRequest;
import com.rensilver.ai_knowledge_assistant.dto.UserResponse;
import com.rensilver.ai_knowledge_assistant.entity.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

import java.util.Arrays;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end coverage for the admin bootstrap + role promotion flow: a real
 * Spring context including SecurityConfig's real @EnableMethodSecurity (so
 * @PreAuthorize is genuinely enforced here, unlike in UserControllerTest's
 * @WebMvcTest slice), a real Testcontainers Postgres, and the real
 * AdminBootstrapRunner.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@TestPropertySource(properties = {
        "app.admin.bootstrap-email=admin-it@example.com",
        "app.admin.bootstrap-password=correct-admin-password"
})
class AdminRoleManagementIT extends PgVectorTestSupport {

    @Autowired
    private TestRestTemplate restTemplate;

    private String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }

    private String loginAndGetToken(String email, String password) {
        ResponseEntity<AuthResponse> response =
                restTemplate.postForEntity("/auth/login", new LoginRequest(email, password), AuthResponse.class);
        return response.getBody().token();
    }

    private HttpHeaders authHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    private UUID findUserId(String adminToken, String email) {
        ResponseEntity<UserResponse[]> response = restTemplate.exchange(
                "/users", HttpMethod.GET, new HttpEntity<>(authHeaders(adminToken)), UserResponse[].class);
        return Arrays.stream(response.getBody())
                .filter(u -> u.email().equals(email))
                .findFirst()
                .orElseThrow()
                .id();
    }

    @Test
    void bootstrappedAdminCanLogInAndHasTheAdminRole() {
        ResponseEntity<AuthResponse> response = restTemplate.postForEntity(
                "/auth/login", new LoginRequest("admin-it@example.com", "correct-admin-password"), AuthResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().role()).isEqualTo("ADMIN");
    }

    @Test
    void adminCanPromoteAndThenDemoteAnotherUser() {
        String adminToken = loginAndGetToken("admin-it@example.com", "correct-admin-password");
        String targetEmail = uniqueEmail();
        restTemplate.postForEntity(
                "/auth/register", new RegisterRequest("Target User", targetEmail, "target-password"), AuthResponse.class);
        UUID targetId = findUserId(adminToken, targetEmail);

        ResponseEntity<UserResponse> promote = restTemplate.exchange(
                "/users/{id}/role", HttpMethod.PATCH,
                new HttpEntity<>(new RoleUpdateRequest(Role.ADMIN), authHeaders(adminToken)),
                UserResponse.class, targetId);
        assertThat(promote.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(promote.getBody().role()).isEqualTo("ADMIN");

        ResponseEntity<UserResponse> demote = restTemplate.exchange(
                "/users/{id}/role", HttpMethod.PATCH,
                new HttpEntity<>(new RoleUpdateRequest(Role.USER), authHeaders(adminToken)),
                UserResponse.class, targetId);
        assertThat(demote.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(demote.getBody().role()).isEqualTo("USER");
    }

    @Test
    void cannotDemoteTheSoleRemainingAdmin() {
        String adminToken = loginAndGetToken("admin-it@example.com", "correct-admin-password");
        UUID adminId = findUserId(adminToken, "admin-it@example.com");

        ResponseEntity<String> response = restTemplate.exchange(
                "/users/{id}/role", HttpMethod.PATCH,
                new HttpEntity<>(new RoleUpdateRequest(Role.USER), authHeaders(adminToken)),
                String.class, adminId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void nonAdminIsForbiddenFromListingUsersOrChangingRoles() {
        String email = uniqueEmail();
        restTemplate.postForEntity(
                "/auth/register", new RegisterRequest("Plain User", email, "plain-password"), AuthResponse.class);
        String userToken = loginAndGetToken(email, "plain-password");

        ResponseEntity<String> listResponse = restTemplate.exchange(
                "/users", HttpMethod.GET, new HttpEntity<>(authHeaders(userToken)), String.class);
        assertThat(listResponse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<String> patchResponse = restTemplate.exchange(
                "/users/{id}/role", HttpMethod.PATCH,
                new HttpEntity<>(new RoleUpdateRequest(Role.ADMIN), authHeaders(userToken)),
                String.class, UUID.randomUUID());
        assertThat(patchResponse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
```

- [ ] **Step 2: Run the test**

Run: `./mvnw test -Dtest=AdminRoleManagementIT`
Expected: `BUILD SUCCESS`, 4 tests passing (needs Docker for Testcontainers).

- [ ] **Step 3: Run the full suite**

Run: `./mvnw verify`
Expected: `BUILD SUCCESS` — confirms nothing in Tasks 1–4 broke any pre-existing test (particularly `AuthControllerIT`, `DocumentControllerTest`, `DocumentServiceTest`).

- [ ] **Step 4: Commit**

```bash
git add src/test/java/com/rensilver/ai_knowledge_assistant/controller/AdminRoleManagementIT.java
git commit -m "Add end-to-end test for admin bootstrap and role promotion"
```

---

## Post-implementation note for CLAUDE.md

Once this plan is fully executed, update the "Status" section of `CLAUDE.md` to record that admin role management is done (bootstrap via `ADMIN_BOOTSTRAP_EMAIL`/`ADMIN_BOOTSTRAP_PASSWORD`, `GET /users` and `PATCH /users/{id}/role`), and remove the implication that `DELETE /documents/{id}`'s `ADMIN` role was ever only reachable via direct database access.
