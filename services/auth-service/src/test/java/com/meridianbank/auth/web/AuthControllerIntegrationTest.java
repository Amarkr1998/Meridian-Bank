package com.meridianbank.auth.web;

import com.meridianbank.auth.web.dto.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full-stack coverage against real Postgres and Redis containers: registration, the login → MFA
 * → tokens flow, refresh/logout, RBAC on the admin-only endpoint, and unauthenticated access.
 * See docs/architecture/onboarding-flow.md and docs/security/security-architecture.md.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class AuthControllerIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired
    private TestRestTemplate restTemplate;

    private String uniqueEmail() {
        return "customer-" + UUID.randomUUID() + "@meridianbank.local";
    }

    private TokenResponse registerLoginAndVerifyMfa(String email, String password) {
        restTemplate.postForEntity("/api/v1/auth/register", new RegisterRequest(email, password), UserResponse.class);

        LoginResponse loginResponse = restTemplate.postForEntity(
                "/api/v1/auth/login", new LoginRequest(email, password), LoginResponse.class).getBody();
        assertThat(loginResponse).isNotNull();
        assertThat(loginResponse.mfaRequired()).isTrue();
        assertThat(loginResponse.devOtp()).isNotBlank();

        ResponseEntity<TokenResponse> verifyResponse = restTemplate.postForEntity("/api/v1/auth/mfa/verify",
                new MfaVerifyRequest(loginResponse.mfaChallengeId(), loginResponse.devOtp()), TokenResponse.class);
        assertThat(verifyResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        return verifyResponse.getBody();
    }

    private HttpEntity<Void> bearer(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        return new HttpEntity<>(headers);
    }

    @Test
    void fullLoginFlow_registerLoginMfaThenAccessProtectedEndpoint() {
        String email = uniqueEmail();
        TokenResponse tokens = registerLoginAndVerifyMfa(email, "Correct-Horse1!");

        assertThat(tokens.accessToken()).isNotBlank();
        assertThat(tokens.role().name()).isEqualTo("CUSTOMER");

        ResponseEntity<MeResponse> me = restTemplate.exchange("/api/v1/auth/me", org.springframework.http.HttpMethod.GET,
                bearer(tokens.accessToken()), MeResponse.class);
        assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(me.getBody().email()).isEqualTo(email);
    }

    @Test
    void refreshThenLogout_revokesToken() {
        TokenResponse tokens = registerLoginAndVerifyMfa(uniqueEmail(), "Correct-Horse1!");

        ResponseEntity<TokenResponse> refreshed = restTemplate.postForEntity(
                "/api/v1/auth/refresh", new RefreshRequest(tokens.refreshToken()), TokenResponse.class);
        assertThat(refreshed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(refreshed.getBody().refreshToken()).isNotEqualTo(tokens.refreshToken());

        // The original refresh token was rotated out and must no longer work.
        ResponseEntity<ErrorResponse> reuseOriginal = restTemplate.postForEntity(
                "/api/v1/auth/refresh", new RefreshRequest(tokens.refreshToken()), ErrorResponse.class);
        assertThat(reuseOriginal.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        restTemplate.postForEntity("/api/v1/auth/logout",
                new LogoutRequest(refreshed.getBody().refreshToken()), Void.class);

        ResponseEntity<ErrorResponse> afterLogout = restTemplate.postForEntity("/api/v1/auth/refresh",
                new RefreshRequest(refreshed.getBody().refreshToken()), ErrorResponse.class);
        assertThat(afterLogout.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void login_withWrongPassword_returnsInvalidCredentials() {
        String email = uniqueEmail();
        restTemplate.postForEntity("/api/v1/auth/register", new RegisterRequest(email, "Correct-Horse1!"), UserResponse.class);

        ResponseEntity<ErrorResponse> response = restTemplate.postForEntity(
                "/api/v1/auth/login", new LoginRequest(email, "wrong-password"), ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().code()).isEqualTo("INVALID_CREDENTIALS");
    }

    @Test
    void meEndpoint_withoutToken_returns401() {
        ResponseEntity<ErrorResponse> response = restTemplate.getForEntity("/api/v1/auth/me", ErrorResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void adminOnlyEndpoint_rejectedForCustomerRole() {
        TokenResponse tokens = registerLoginAndVerifyMfa(uniqueEmail(), "Correct-Horse1!");

        ResponseEntity<ErrorResponse> response = restTemplate.exchange("/api/v1/auth/users",
                org.springframework.http.HttpMethod.GET, bearer(tokens.accessToken()), ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().code()).isEqualTo("ACCESS_DENIED");
    }

    @Test
    void adminOnlyEndpoint_allowedForBootstrappedAdmin() {
        // AdminBootstrapRunner seeds this account on startup (meridian.admin-bootstrap.*).
        TokenResponse tokens = loginBootstrapAdmin();

        ResponseEntity<String> response = restTemplate.exchange("/api/v1/auth/users",
                org.springframework.http.HttpMethod.GET, bearer(tokens.accessToken()), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private TokenResponse loginBootstrapAdmin() {
        LoginResponse loginResponse = restTemplate.postForEntity("/api/v1/auth/login",
                new LoginRequest("admin@meridianbank.local", "ChangeMe!Local1"), LoginResponse.class).getBody();
        assertThat(loginResponse).isNotNull();
        assertThat(loginResponse.mfaRequired()).isTrue();
        return restTemplate.postForEntity("/api/v1/auth/mfa/verify",
                new MfaVerifyRequest(loginResponse.mfaChallengeId(), loginResponse.devOtp()), TokenResponse.class)
                .getBody();
    }
}
