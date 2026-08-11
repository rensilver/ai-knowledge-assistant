package com.rensilver.ai_knowledge_assistant.controller;

import com.rensilver.ai_knowledge_assistant.PgVectorTestSupport;
import com.rensilver.ai_knowledge_assistant.dto.AuthResponse;
import com.rensilver.ai_knowledge_assistant.dto.LoginRequest;
import com.rensilver.ai_knowledge_assistant.dto.RegisterRequest;
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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end coverage of the auth flow noted as untested in CLAUDE.md's next
 * steps: a real Spring context, a real (Testcontainers) Postgres, real
 * password hashing/JWT signing — register, reject a duplicate email, log in,
 * and use the issued token against a protected endpoint, all through the HTTP
 * layer rather than calling {@code AuthService} directly.
 *
 * <p>No running Ollama is needed: the model beans are constructed without
 * connecting (see {@link com.rensilver.ai_knowledge_assistant.AiKnowledgeAssistantApplicationTests}),
 * and none of these endpoints trigger a model call.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class AuthControllerIT extends PgVectorTestSupport {

    @Autowired
    private TestRestTemplate restTemplate;

    private String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }

    @Test
    void registersLogsInAndAuthenticatesAProtectedRequestWithTheIssuedToken() {
        String email = uniqueEmail();
        RegisterRequest registerRequest = new RegisterRequest("Ada Lovelace", email, "correct-password");

        ResponseEntity<AuthResponse> registerResponse =
                restTemplate.postForEntity("/auth/register", registerRequest, AuthResponse.class);
        assertThat(registerResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(registerResponse.getBody()).isNotNull();
        assertThat(registerResponse.getBody().token()).isNotBlank();

        LoginRequest loginRequest = new LoginRequest(email, "correct-password");
        ResponseEntity<AuthResponse> loginResponse =
                restTemplate.postForEntity("/auth/login", loginRequest, AuthResponse.class);
        assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        String token = loginResponse.getBody().token();
        assertThat(token).isNotBlank();

        HttpHeaders authHeaders = new HttpHeaders();
        authHeaders.setBearerAuth(token);
        ResponseEntity<String> protectedResponse = restTemplate.exchange(
                "/documents", HttpMethod.GET, new HttpEntity<>(authHeaders), String.class);
        assertThat(protectedResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void rejectsRegistrationWithAnEmailAlreadyInUse() {
        String email = uniqueEmail();
        RegisterRequest request = new RegisterRequest("First User", email, "password123");
        restTemplate.postForEntity("/auth/register", request, AuthResponse.class);

        ResponseEntity<String> secondAttempt =
                restTemplate.postForEntity("/auth/register", request, String.class);

        assertThat(secondAttempt.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void rejectsLoginWithTheWrongPassword() {
        String email = uniqueEmail();
        restTemplate.postForEntity(
                "/auth/register", new RegisterRequest("Ada Lovelace", email, "correct-password"), AuthResponse.class);

        ResponseEntity<String> loginResponse = restTemplate.postForEntity(
                "/auth/login", new LoginRequest(email, "wrong-password"), String.class);

        assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void rejectsProtectedRequestsWithNoToken() {
        ResponseEntity<String> response = restTemplate.getForEntity("/documents", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void rejectsRegistrationWithAnInvalidPayload() {
        RegisterRequest blankEmail = new RegisterRequest("Ada Lovelace", "", "password123");

        ResponseEntity<String> response = restTemplate.postForEntity("/auth/register", blankEmail, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
