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
