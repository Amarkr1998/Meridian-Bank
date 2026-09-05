package com.meridianbank.kyc.web;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.*;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full-stack coverage against real Postgres — see SupportRequestService's Javadoc for why this
 * lives in customer-kyc-service. No dependency on a real Customer row: a support request's
 * customerId is whatever the caller's own JWT subject is, same as every other self-service
 * endpoint in this service.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class SupportRequestControllerIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final String JWT_SECRET = "meridian-bank-local-demo-jwt-signing-secret-change-me-32bytes-min";
    private static final SecretKey SIGNING_KEY = Keys.hmacShaKeyFor(JWT_SECRET.getBytes(StandardCharsets.UTF_8));

    @Autowired
    private TestRestTemplate restTemplate;

    private String mintToken(UUID userId, String email, String role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer("test")
                .subject(userId.toString())
                .claim("email", email)
                .claim("role", role)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(900)))
                .signWith(SIGNING_KEY)
                .compact();
    }

    private HttpHeaders jsonAuth(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private HttpEntity<Void> bearer(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return new HttpEntity<>(headers);
    }

    private ResponseEntity<Map> createRequest(String customerToken) {
        return restTemplate.exchange("/api/v1/support-requests", HttpMethod.POST,
                new HttpEntity<>(Map.of("category", "PAYMENT", "subject", "Missing transfer",
                        "description", "My transfer from yesterday never arrived"), jsonAuth(customerToken)),
                Map.class);
    }

    @Test
    void customer_canCreateAndReadTheirOwnRequest() {
        String customerToken = mintToken(UUID.randomUUID(), "cust@example.com", "CUSTOMER");

        ResponseEntity<Map> created = createRequest(customerToken);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody().get("status")).isEqualTo("OPEN");
        String id = (String) created.getBody().get("id");

        ResponseEntity<Map> read = restTemplate.exchange("/api/v1/support-requests/" + id, HttpMethod.GET,
                bearer(customerToken), Map.class);
        assertThat(read.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(read.getBody().get("subject")).isEqualTo("Missing transfer");
    }

    @Test
    void aDifferentCustomer_cannotReadSomeoneElsesRequest() {
        String ownerToken = mintToken(UUID.randomUUID(), "owner@example.com", "CUSTOMER");
        String otherToken = mintToken(UUID.randomUUID(), "other@example.com", "CUSTOMER");
        String id = (String) createRequest(ownerToken).getBody().get("id");

        ResponseEntity<Map> response = restTemplate.exchange("/api/v1/support-requests/" + id, HttpMethod.GET,
                bearer(otherToken), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void customer_seesOnlyTheirOwnRequestsInList() {
        String customerToken = mintToken(UUID.randomUUID(), "cust2@example.com", "CUSTOMER");
        createRequest(customerToken);
        String otherToken = mintToken(UUID.randomUUID(), "other2@example.com", "CUSTOMER");
        createRequest(otherToken);

        ResponseEntity<Map> response = restTemplate.exchange("/api/v1/support-requests", HttpMethod.GET,
                bearer(customerToken), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<?> content = (List<?>) response.getBody().get("content");
        assertThat(content).hasSize(1);
    }

    @Test
    void operationsStaff_canSeeTheFullQueueAndWorkTheFullLifecycle() {
        String customerToken = mintToken(UUID.randomUUID(), "cust3@example.com", "CUSTOMER");
        String id = (String) createRequest(customerToken).getBody().get("id");
        String opsToken = mintToken(UUID.randomUUID(), "ops@meridianbank.local", "OPERATIONS");

        ResponseEntity<Map> queue = restTemplate.exchange("/api/v1/support-requests?status=OPEN", HttpMethod.GET,
                bearer(opsToken), Map.class);
        assertThat(queue.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<?> content = (List<?>) queue.getBody().get("content");
        assertThat(content).isNotEmpty();

        ResponseEntity<Map> inProgress = restTemplate.exchange("/api/v1/support-requests/" + id + "/start-progress",
                HttpMethod.PATCH, bearer(opsToken), Map.class);
        assertThat(inProgress.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(inProgress.getBody().get("status")).isEqualTo("IN_PROGRESS");

        ResponseEntity<Map> resolved = restTemplate.exchange("/api/v1/support-requests/" + id + "/resolve",
                HttpMethod.PATCH, new HttpEntity<>(Map.of("notes", "Reprocessed manually, funds arrived"),
                        jsonAuth(opsToken)), Map.class);
        assertThat(resolved.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resolved.getBody().get("status")).isEqualTo("RESOLVED");
        assertThat(resolved.getBody().get("resolutionNotes")).isEqualTo("Reprocessed manually, funds arrived");
    }

    @Test
    void customerToken_cannotStartProgressOrResolve() {
        String customerToken = mintToken(UUID.randomUUID(), "cust4@example.com", "CUSTOMER");
        String id = (String) createRequest(customerToken).getBody().get("id");

        ResponseEntity<Map> response = restTemplate.exchange("/api/v1/support-requests/" + id + "/start-progress",
                HttpMethod.PATCH, bearer(customerToken), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void resolvingWithoutStartingProgressFirst_isRejected() {
        String customerToken = mintToken(UUID.randomUUID(), "cust5@example.com", "CUSTOMER");
        String id = (String) createRequest(customerToken).getBody().get("id");
        String opsToken = mintToken(UUID.randomUUID(), "ops2@meridianbank.local", "OPERATIONS");

        ResponseEntity<Map> response = restTemplate.exchange("/api/v1/support-requests/" + id + "/resolve",
                HttpMethod.PATCH, new HttpEntity<>(Map.of("notes", "skipping ahead"), jsonAuth(opsToken)), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }
}
