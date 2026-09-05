package com.meridianbank.audit.web;

import com.meridianbank.audit.domain.AuditEvent;
import com.meridianbank.audit.repository.AuditEventRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
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
 * Full-stack coverage against real Postgres (Testcontainers) for the read-only audit-query API —
 * see docs/adr/0012-audit-architecture.md. Ingestion (the Kafka consumer path) is covered
 * separately by AuditEventConsumerIntegrationTest against a real broker; this test seeds rows
 * directly via the repository so it can focus purely on RBAC and filtering. No Kafka container is
 * declared here, so {@code spring.kafka.bootstrap-servers} is deliberately pointed at a
 * non-existent address rather than left at its {@code localhost:9092} default — otherwise, on a
 * machine that happens to have the real docker-compose stack running locally, this service's
 * consumer would silently connect to the *real* broker and ingest real historical audit.event
 * messages, making this test non-hermetic.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.kafka.bootstrap-servers=localhost:1")
@Testcontainers
class AuditEventControllerIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final String JWT_SECRET = "meridian-bank-local-demo-jwt-signing-secret-change-me-32bytes-min";
    private static final SecretKey SIGNING_KEY = Keys.hmacShaKeyFor(JWT_SECRET.getBytes(StandardCharsets.UTF_8));

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private AuditEventRepository repository;

    private UUID actorId;
    private UUID resourceId;

    @BeforeEach
    void seedEvents() {
        actorId = UUID.randomUUID();
        resourceId = UUID.randomUUID();
        repository.save(new AuditEvent(UUID.randomUUID(), "audit.event", Instant.now(), "corr-1",
                "account-service", actorId, "OPERATIONS", "ACCOUNT_FROZEN", "account", resourceId, "SUCCESS",
                "seeded"));
        repository.save(new AuditEvent(UUID.randomUUID(), "audit.event", Instant.now(), "corr-2",
                "payment-service", UUID.randomUUID(), "CUSTOMER", "PAYMENT_COMPLETED", "transaction",
                UUID.randomUUID(), "SUCCESS", "seeded"));
    }

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

    private HttpEntity<Void> bearer(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return new HttpEntity<>(headers);
    }

    @Test
    void auditor_canListAndReadAuditEvents() {
        String token = mintToken(UUID.randomUUID(), "auditor@meridianbank.local", "AUDITOR");

        ResponseEntity<Map> response = restTemplate.exchange("/api/v1/audit-events", HttpMethod.GET,
                bearer(token), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((List<?>) response.getBody().get("content")).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    void complianceOfficerAndAdmin_canAlsoList() {
        String complianceToken = mintToken(UUID.randomUUID(), "compliance@meridianbank.local", "COMPLIANCE_OFFICER");
        String adminToken = mintToken(UUID.randomUUID(), "admin@meridianbank.local", "ADMIN");

        assertThat(restTemplate.exchange("/api/v1/audit-events", HttpMethod.GET, bearer(complianceToken), Map.class)
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(restTemplate.exchange("/api/v1/audit-events", HttpMethod.GET, bearer(adminToken), Map.class)
                .getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void nonGovernanceRoles_areRejectedWith403() {
        String customerToken = mintToken(UUID.randomUUID(), "customer@example.com", "CUSTOMER");
        String opsToken = mintToken(UUID.randomUUID(), "ops@meridianbank.local", "OPERATIONS");
        String riskToken = mintToken(UUID.randomUUID(), "risk@meridianbank.local", "RISK_ANALYST");

        assertThat(restTemplate.exchange("/api/v1/audit-events", HttpMethod.GET, bearer(customerToken), Map.class)
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(restTemplate.exchange("/api/v1/audit-events", HttpMethod.GET, bearer(opsToken), Map.class)
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(restTemplate.exchange("/api/v1/audit-events", HttpMethod.GET, bearer(riskToken), Map.class)
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void unauthenticatedRequest_isRejectedWith401() {
        ResponseEntity<Map> response = restTemplate.getForEntity("/api/v1/audit-events", Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void filteringByActorId_returnsOnlyMatchingEvents() {
        String token = mintToken(UUID.randomUUID(), "auditor@meridianbank.local", "AUDITOR");

        ResponseEntity<Map> response = restTemplate.exchange("/api/v1/audit-events?actorId=" + actorId,
                HttpMethod.GET, bearer(token), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> content = (List<Map<String, Object>>) response.getBody().get("content");
        assertThat(content).hasSize(1);
        assertThat(content.get(0).get("action")).isEqualTo("ACCOUNT_FROZEN");
    }

    @Test
    void getById_returnsDetailAndUnknownIdReturns404() {
        String token = mintToken(UUID.randomUUID(), "auditor@meridianbank.local", "AUDITOR");
        AuditEvent seeded = repository.findAll().stream()
                .filter(e -> e.getActorId().equals(actorId)).findFirst().orElseThrow();

        ResponseEntity<Map> found = restTemplate.exchange("/api/v1/audit-events/" + seeded.getId(),
                HttpMethod.GET, bearer(token), Map.class);
        assertThat(found.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(found.getBody().get("resourceId")).isEqualTo(resourceId.toString());

        ResponseEntity<Map> notFound = restTemplate.exchange("/api/v1/audit-events/" + UUID.randomUUID(),
                HttpMethod.GET, bearer(token), Map.class);
        assertThat(notFound.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void noMutatingEndpointExists_postIsRejected() {
        String token = mintToken(UUID.randomUUID(), "admin@meridianbank.local", "ADMIN");
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<String> response = restTemplate.exchange("/api/v1/audit-events", HttpMethod.POST,
                new HttpEntity<>("{}", headers), String.class);

        // No POST/PATCH/DELETE handler is ever mapped on this controller — even an ADMIN token
        // cannot create or mutate audit history through this API (see SecurityConfig's Javadoc).
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
    }
}
