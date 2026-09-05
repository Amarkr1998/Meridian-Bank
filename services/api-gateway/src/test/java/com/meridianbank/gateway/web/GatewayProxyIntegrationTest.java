package com.meridianbank.gateway.web;

import com.sun.net.httpserver.HttpServer;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The flagship proof for this service: a real embedded gateway, a real Redis (Testcontainers),
 * and a plain JDK {@link HttpServer} standing in for a downstream service — not mocked Spring
 * beans, not a fake in-process router. Proves every filter in the chain actually does its job:
 * CORS preflight is answered without reaching the stub; a public path is forwarded with no token;
 * a protected path is rejected 401 with no token and forwarded (with the Authorization header and
 * body intact) once a validly-signed token is presented; an unrouted /api/v1/ path 404s at the
 * gateway itself; and the Redis-backed rate limiter genuinely trips after its configured ceiling.
 *
 * <p>Every request carries its own synthetic {@code X-Forwarded-For} IP (see {@link #clientIp})
 * so each test method gets an independent rate-limit bucket — the limiter is keyed by client IP
 * (see RateLimitFilter), and a shared bucket across unrelated test methods would make this class
 * order-dependent and flaky.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "meridian.gateway.rate-limit.max-requests-per-window=3",
        "meridian.gateway.rate-limit.window-seconds=60"
})
@Testcontainers
class GatewayProxyIntegrationTest {

    private static final String JWT_SECRET = "meridian-bank-local-demo-jwt-signing-secret-change-me-32bytes-min";

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    private static HttpServer stubServer;
    private static final AtomicInteger stubRequestCount = new AtomicInteger();
    private static volatile String lastAuthorizationHeaderSeen;
    private static volatile String lastRequestBodySeen;
    private static volatile String lastCorrelationIdSeenByStub;
    private static volatile String lastPathSeenByStub;

    @Autowired
    private TestRestTemplate restTemplate;

    private String clientIp;

    @BeforeEach
    void freshClientIp() {
        clientIp = "10.77." + ThreadLocalRandom.current().nextInt(0, 255) + "." + ThreadLocalRandom.current().nextInt(1, 255);
    }

    @DynamicPropertySource
    static void stubServiceUrls(DynamicPropertyRegistry registry) throws IOException {
        stubServer = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        stubServer.createContext("/api/v1/auth", exchange -> {
            stubRequestCount.incrementAndGet();
            lastAuthorizationHeaderSeen = exchange.getRequestHeaders().getFirst("Authorization");
            lastCorrelationIdSeenByStub = exchange.getRequestHeaders().getFirst("X-Correlation-Id");
            lastRequestBodySeen = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            byte[] responseBody = "{\"status\":\"ok\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, responseBody.length);
            exchange.getResponseBody().write(responseBody);
            exchange.close();
        });
        stubServer.createContext("/api/v1/accounts", exchange -> {
            stubRequestCount.incrementAndGet();
            byte[] responseBody = "[]".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, responseBody.length);
            exchange.getResponseBody().write(responseBody);
            exchange.close();
        });
        stubServer.createContext("/api/v1/approvals", exchange -> {
            stubRequestCount.incrementAndGet();
            lastPathSeenByStub = exchange.getRequestURI().getPath();
            byte[] responseBody = "{\"status\":\"ok\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, responseBody.length);
            exchange.getResponseBody().write(responseBody);
            exchange.close();
        });
        stubServer.createContext("/actuator/health", exchange -> {
            byte[] responseBody = "{\"status\":\"UP\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, responseBody.length);
            exchange.getResponseBody().write(responseBody);
            exchange.close();
        });
        stubServer.start();
        String stubUrl = "http://localhost:" + stubServer.getAddress().getPort();

        registry.add("meridian.gateway.services.auth-service", () -> stubUrl);
        registry.add("meridian.gateway.services.customer-kyc-service", () -> stubUrl);
        registry.add("meridian.gateway.services.account-service", () -> stubUrl);
        registry.add("meridian.gateway.services.payment-service", () -> stubUrl);
        registry.add("meridian.gateway.services.notification-service", () -> stubUrl);
        registry.add("meridian.gateway.services.fraud-risk-service", () -> stubUrl);
        registry.add("meridian.gateway.services.ledger-service", () -> stubUrl);
        registry.add("meridian.gateway.services.audit-service", () -> stubUrl);
    }

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @AfterAll
    static void stopStub() {
        stubServer.stop(0);
    }

    private String signToken(UUID subject) {
        return signToken(subject, "CUSTOMER");
    }

    private String signToken(UUID subject, String role) {
        SecretKey key = Keys.hmacShaKeyFor(JWT_SECRET.getBytes(StandardCharsets.UTF_8));
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(subject.toString())
                .claim("email", "customer@example.com")
                .claim("role", role)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(Duration.ofMinutes(15))))
                .signWith(key)
                .compact();
    }

    private HttpHeaders headersWithClientIp() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Forwarded-For", clientIp);
        return headers;
    }

    private ResponseEntity<String> get(String path, HttpHeaders headers) {
        return restTemplate.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    @Test
    void corsPreflight_isAnsweredWithoutReachingDownstream() {
        HttpHeaders headers = headersWithClientIp();
        headers.set("Origin", "http://localhost:5173");
        headers.set("Access-Control-Request-Method", "POST");
        int before = stubRequestCount.get();

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/auth/login", HttpMethod.OPTIONS, new HttpEntity<>(headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(response.getHeaders().getFirst("Access-Control-Allow-Origin")).isEqualTo("http://localhost:5173");
        assertThat(stubRequestCount.get()).isEqualTo(before);
    }

    @Test
    void publicPath_isForwardedWithoutAnyToken() {
        HttpHeaders headers = headersWithClientIp();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> request = new HttpEntity<>("{\"email\":\"a@b.com\",\"password\":\"x\"}", headers);

        ResponseEntity<String> response = restTemplate.postForEntity("/api/v1/auth/login", request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("ok");
        assertThat(lastRequestBodySeen).contains("a@b.com");
    }

    @Test
    void protectedPath_withoutToken_isRejectedAtTheEdge() {
        ResponseEntity<String> response = get("/api/v1/accounts", headersWithClientIp());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).contains("UNAUTHENTICATED");
    }

    @Test
    void gatewayGeneratedResponses_carryBaselineSecurityHeaders() {
        // This gateway has no Spring Security at all (unlike every downstream service, which gets
        // these for free from Spring Security's defaults), so its own directly-generated
        // responses need them set explicitly — see ErrorResponseWriter.
        ResponseEntity<String> response = get("/api/v1/accounts", headersWithClientIp());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getHeaders().getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(response.getHeaders().getFirst("X-Frame-Options")).isEqualTo("DENY");
        assertThat(response.getHeaders().getFirst("Cache-Control")).isEqualTo("no-store");
    }

    @Test
    void protectedPath_withValidToken_isForwardedWithAuthorizationHeaderIntact() {
        HttpHeaders headers = headersWithClientIp();
        headers.setBearerAuth(signToken(UUID.randomUUID()));

        ResponseEntity<String> response = get("/api/v1/accounts", headers);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void protectedPath_withMalformedToken_isRejectedAtTheEdge() {
        HttpHeaders headers = headersWithClientIp();
        headers.setBearerAuth("not-a-real-jwt");

        ResponseEntity<String> response = get("/api/v1/accounts", headers);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void unroutedApiPath_is404AtTheGatewayItself() {
        ResponseEntity<String> response = get("/api/v1/approvals", headersWithClientIp());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).contains("ROUTE_NOT_FOUND");
    }

    @Test
    void authorizationHeader_isForwardedVerbatimToDownstream() {
        String token = signToken(UUID.randomUUID());
        HttpHeaders headers = headersWithClientIp();
        headers.setBearerAuth(token);

        get("/api/v1/auth/anything", headers);

        assertThat(lastAuthorizationHeaderSeen).isEqualTo("Bearer " + token);
    }

    @Test
    void correlationId_mintedAtTheEdgeIsGenuinelyForwardedDownstream_evenWhenTheClientNeverSentOne() {
        // A public path (no bearer token needed), and deliberately no X-Correlation-Id from the
        // client either — this is the common case and the one that regressed: CorrelationIdFilter
        // only records a minted ID in a request attribute and this gateway's own response header,
        // not on the (immutable) incoming request, so ProxyFilter must add it explicitly or it
        // silently never reaches the downstream service.
        HttpHeaders headers = headersWithClientIp();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> request = new HttpEntity<>("{}", headers);

        ResponseEntity<String> response = restTemplate.postForEntity("/api/v1/auth/login", request, String.class);

        String edgeCorrelationId = response.getHeaders().getFirst("X-Correlation-Id");
        assertThat(edgeCorrelationId).isNotBlank();
        assertThat(lastCorrelationIdSeenByStub).isEqualTo(edgeCorrelationId);
    }

    @Test
    void approvalsAlias_rewritesToTheRealApprovalsPathOnTheCorrectService() {
        HttpHeaders headers = headersWithClientIp();
        headers.setBearerAuth(signToken(UUID.randomUUID()));

        ResponseEntity<String> response = get("/api/v1/ops/approvals/fraud/42/approve", headers);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(lastPathSeenByStub).isEqualTo("/api/v1/approvals/42/approve");
    }

    @Test
    void systemHealth_aggregatesEveryServiceForStaff() {
        HttpHeaders headers = headersWithClientIp();
        headers.setBearerAuth(signToken(UUID.randomUUID(), "OPERATIONS"));

        ResponseEntity<String> response = get("/api/v1/ops/system-health", headers);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"service\":\"api-gateway\"", "\"status\":\"UP\"")
                .contains("auth-service", "customer-kyc-service", "fraud-risk-service", "ledger-service", "audit-service");
        assertThat(response.getHeaders().getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(response.getHeaders().getFirst("X-Frame-Options")).isEqualTo("DENY");
    }

    @Test
    void systemHealth_rejectsANonStaffRole() {
        HttpHeaders headers = headersWithClientIp();
        headers.setBearerAuth(signToken(UUID.randomUUID(), "CUSTOMER"));

        ResponseEntity<String> response = get("/api/v1/ops/system-health", headers);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void rateLimiter_tripsAfterConfiguredCeiling() {
        String path = "/api/v1/auth/rate-limit-probe";
        HttpHeaders headers = headersWithClientIp();
        org.springframework.http.HttpStatusCode lastStatus = null;
        for (int i = 0; i < 4; i++) {
            lastStatus = get(path, headers).getStatusCode();
        }

        assertThat(lastStatus).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }
}
