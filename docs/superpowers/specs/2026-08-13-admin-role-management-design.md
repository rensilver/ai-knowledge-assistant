# Admin bootstrap + role promotion — design

## Context

`Role.ADMIN` (`entity/Role.java`) gates `DELETE /documents/{id}` via
`@PreAuthorize("hasRole('ADMIN')")` in `DocumentController`, but nothing in
the application can ever grant that role: `AuthService.register()` hardcodes
`Role.USER` for every self-registered account, and there is no endpoint that
changes a user's role afterward. The only way an `ADMIN` row has ever existed
is a direct `UPDATE users SET role = 'ADMIN' ...` against Postgres.

That's fine for local dev with `psql` on hand, but the project is about to
deploy with the backend and frontend on separate cloud providers. At that
point, direct DB access becomes an infra-level credential handed to whoever
needs to manage users — no audit trail, no application-level business rules
(nothing stops demoting the last admin), and unusable from the frontend at
all. This spec closes that gap by giving the app itself the only path to
`ADMIN`.

## Goals

- Every fresh deployment ends up with exactly one working `ADMIN` account,
  created without anyone touching the database directly.
- Frontend gets a real API to promote/demote users and list them, protected
  the same way `DELETE /documents/{id}` already is.
- Role changes are logged (who changed whom, old role, new role) using the
  existing structured logging + correlation-id setup — no new audit table.
- No path exists to leave the system with zero admins.

## Design

### 1. `AdminBootstrapRunner` (new `config/AdminBootstrapRunner.java`)

An `ApplicationRunner` bean that, on every startup:

1. Reads `ADMIN_BOOTSTRAP_EMAIL` / `ADMIN_BOOTSTRAP_PASSWORD` via
   `@Value("${ADMIN_BOOTSTRAP_EMAIL:}")` (empty-string default, no
   hardcoded credential — unlike `JWT_SECRET`'s dev default, seeding a real
   password into source control is exactly the anti-pattern this spec
   removes).
2. If either is blank, no-op. Local dev without them behaves exactly as
   today.
3. If `userRepository.existsByEmail(email)` is already `true`, no-op — never
   overwrites an existing user's password or role on restart.
4. Otherwise, validates email/password with the same constraints
   `RegisterRequest` already enforces (reusing `jakarta.validation`, not
   duplicating rules). On failure, logs a warning and skips — a typo'd env
   var must not crash the app at boot.
5. Creates one `UserEntity` with `role = ADMIN`, password hashed via the
   existing `PasswordEncoder` bean, name `"Admin"`.

Idempotency is the `existsByEmail` check; no new migration, table, or flag.

### 2. `UserService` (new `service/UserService.java`)

- `list(): List<UserResponse>` — `userRepository.findAll()` mapped to the
  response DTO.
- `changeRole(UUID id, Role newRole, String actorEmail): UserResponse`:
  1. Load the user or throw `UserNotFoundException` (same shape as
     `DocumentNotFoundException`).
  2. If `user.getRole() == newRole`, return as-is (idempotent no-op).
  3. If demoting an `ADMIN` to `USER`, count remaining admins via a new
     `UserRepository.countByRole(Role role)`. If the count is `1` (this user
     is the last one), throw `LastAdminException`.
  4. Otherwise set the role, save, log at `INFO`
     (`actorEmail`, `user.getId()`, old role, new role), return the DTO.

Self-demotion is not special-cased beyond the last-admin rule: an admin may
change their own role as long as at least one other admin remains.

### 3. `UserController` (new `controller/UserController.java`)

Both endpoints `@PreAuthorize("hasRole('ADMIN')")`, same pattern as
`DocumentController.delete`:

- `GET /users` → `200`, `List<UserResponse>`.
- `PATCH /users/{id}/role` → body `RoleUpdateRequest { Role role }` (bean
  validation via `@NotNull`) → `200`, `UserResponse`. Error responses:
  `404` (`UserNotFoundException`), `409` (`LastAdminException`), `400`
  (validation), `403` (non-admin caller, existing `AccessDeniedException`
  handler).

### 4. New exceptions and `GlobalExceptionHandler` wiring

- `UserNotFoundException` — mirrors `DocumentNotFoundException` → `404`.
- `LastAdminException` — new → `409 CONFLICT`, message "cannot demote the
  last remaining admin", registered in `GlobalExceptionHandler` the same way
  `EmailAlreadyInUseException` is.

### 5. New DTOs (`dto/` package, records, matching `DocumentResponse` style)

- `UserResponse(UUID id, String name, String email, String role, Instant createdAt)`
  with a `static from(UserEntity)` factory.
- `RoleUpdateRequest(@NotNull Role role)`.

### 6. `UserRepository` addition

- `long countByRole(Role role)` — backs the last-admin guard.

### Out of scope

- No `SecurityConfig` changes — both new endpoints fall under the existing
  `anyRequest().authenticated()` rule, and method security already handles
  the role check.
- No self-demotion special case beyond the last-admin guard (explicitly
  decided against — an extra rule with no clear benefit once that guard
  exists).
- No dedicated audit table — structured logs + correlation id are enough for
  this project's current observability setup.
- No password reset / email invite flow for promoted users — out of scope,
  they already have an account and password from self-registration.

## Testing

- **`AdminBootstrapRunnerTest`** (unit): env vars unset → no-op; env vars set
  + no existing user → admin created with hashed password; email already
  exists → no-op, existing row untouched; malformed email/password → warns
  and skips, doesn't throw.
- **`UserServiceTest`** (unit): idempotent no-op when role unchanged;
  last-admin demotion → `LastAdminException`; unknown id →
  `UserNotFoundException`; successful promotion and demotion (non-last-admin
  case) persist and return the updated DTO.
- **`UserControllerTest`** (`@WebMvcTest`, `@MockitoBean` for `UserService`):
  `403` for a non-admin caller on both endpoints (mirrors the existing
  `DocumentControllerTest` delete-authorization test); `400` on missing/
  invalid `role` in the request body.
- **Integration** (extends the `*IT` suite pattern, real Postgres via
  Testcontainers): start the app with `ADMIN_BOOTSTRAP_EMAIL`/`_PASSWORD`
  set, log in with those credentials, confirm `role: "ADMIN"` in the
  response; then, as that admin, `PATCH` a second (seeded) user to `ADMIN`
  and back down to `USER`, and confirm attempting to demote the sole
  remaining admin returns `409`.
