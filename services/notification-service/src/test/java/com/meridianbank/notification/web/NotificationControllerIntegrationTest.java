package com.meridianbank.notification.web;

import com.meridianbank.notification.domain.Notification;
import com.meridianbank.notification.domain.NotificationType;
import com.meridianbank.notification.repository.NotificationRepository;
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
 * Full-stack coverage against real Postgres for the notification inbox API — seeds rows directly
 * via the repository (ingestion is covered separately by NotificationEventConsumerIntegrationTest)
 * so this test can focus purely on RBAC, ownership, and the mark-read workflow. No Kafka container
 * is declared, so {@code spring.kafka.bootstrap-servers} is deliberately pointed at a
 * non-existent address rather than left at its {@code localhost:9092} default — otherwise, on a
 * machine that happens to have the real docker-compose stack running locally, this service's
 * consumers would silently connect to the *real* broker and ingest real historical messages,
 * making this test non-hermetic. The consumer containers fail to connect and keep retrying in the
 * background, which is harmless — see application.yml's Kafka health check being disabled for the
 * same reason.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.kafka.bootstrap-servers=localhost:1")
@Testcontainers
class NotificationControllerIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final String JWT_SECRET = "meridian-bank-local-demo-jwt-signing-secret-change-me-32bytes-min";
    private static final SecretKey SIGNING_KEY = Keys.hmacShaKeyFor(JWT_SECRET.getBytes(StandardCharsets.UTF_8));

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private NotificationRepository repository;

    private UUID customerId;
    private Notification ownNotification;

    @BeforeEach
    void seed() {
        customerId = UUID.randomUUID();
        ownNotification = repository.save(new Notification(UUID.randomUUID(), customerId,
                NotificationType.PAYMENT_SUCCESS, "Payment completed", "Your payment of 10.00 USD completed"));
        repository.save(new Notification(UUID.randomUUID(), UUID.randomUUID(), NotificationType.KYC_STATUS_CHANGED,
                "KYC updated", "Someone else's notification"));
    }

    private String mintToken(UUID userId, String role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer("test")
                .subject(userId.toString())
                .claim("email", "user@example.com")
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
    void customer_seesOnlyTheirOwnNotifications() {
        String token = mintToken(customerId, "CUSTOMER");

        ResponseEntity<Map> response = restTemplate.exchange("/api/v1/notifications", HttpMethod.GET,
                bearer(token), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<?> content = (List<?>) response.getBody().get("content");
        assertThat(content).hasSize(1);
    }

    @Test
    void customer_canReadTheirOwnNotificationDetail() {
        String token = mintToken(customerId, "CUSTOMER");

        ResponseEntity<Map> response = restTemplate.exchange("/api/v1/notifications/" + ownNotification.getId(),
                HttpMethod.GET, bearer(token), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("title")).isEqualTo("Payment completed");
    }

    @Test
    void customer_cannotReadSomeoneElsesNotification() {
        String token = mintToken(UUID.randomUUID(), "CUSTOMER");

        ResponseEntity<Map> response = restTemplate.exchange("/api/v1/notifications/" + ownNotification.getId(),
                HttpMethod.GET, bearer(token), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void customer_canMarkTheirOwnNotificationRead() {
        String token = mintToken(customerId, "CUSTOMER");

        ResponseEntity<Map> response = restTemplate.exchange("/api/v1/notifications/" + ownNotification.getId()
                + "/read", HttpMethod.PATCH, bearer(token), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("read")).isEqualTo(true);
    }

    @Test
    void customer_cannotMarkSomeoneElsesNotificationRead() {
        String token = mintToken(UUID.randomUUID(), "CUSTOMER");

        ResponseEntity<Map> response = restTemplate.exchange("/api/v1/notifications/" + ownNotification.getId()
                + "/read", HttpMethod.PATCH, bearer(token), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void staff_canFilterByCustomerId() {
        String opsToken = mintToken(UUID.randomUUID(), "OPERATIONS");

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/notifications?customerId=" + customerId, HttpMethod.GET, bearer(opsToken), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<?> content = (List<?>) response.getBody().get("content");
        assertThat(content).hasSize(1);
    }

    @Test
    void unauthenticatedRequest_isRejectedWith401() {
        ResponseEntity<Map> response = restTemplate.getForEntity("/api/v1/notifications", Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
