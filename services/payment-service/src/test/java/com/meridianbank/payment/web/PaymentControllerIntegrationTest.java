package com.meridianbank.payment.web;

import com.meridianbank.payment.approval.ApprovalRequestResponse;
import com.meridianbank.payment.approval.RequestReleaseRequest;
import com.meridianbank.payment.client.AccountServiceClient;
import com.meridianbank.payment.client.CustomerKycServiceClient;
import com.meridianbank.payment.client.FraudRiskServiceClient;
import com.meridianbank.payment.client.LedgerServiceClient;
import com.meridianbank.payment.approval.ApprovalDecisionRequest;
import com.meridianbank.payment.web.dto.CreatePaymentRequest;
import com.meridianbank.payment.web.dto.ErrorResponse;
import com.meridianbank.payment.web.dto.PaymentResponse;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.crypto.SecretKey;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Full-stack coverage against real Postgres + Redis, with AccountServiceClient and
 * CustomerKycServiceClient stubbed via @MockitoBean (their own services' behavior is covered in
 * their own test suites). Proves the two things this phase exists for: the validation pipeline,
 * and idempotency — including a genuine concurrent-duplicate race against real Redis. See
 * payment-service/README.md.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class PaymentControllerIntegrationTest {

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

    private static final String JWT_SECRET = "meridian-bank-local-demo-jwt-signing-secret-change-me-32bytes-min";
    private static final SecretKey SIGNING_KEY = Keys.hmacShaKeyFor(JWT_SECRET.getBytes(StandardCharsets.UTF_8));

    @Autowired
    private TestRestTemplate restTemplate;

    @MockitoBean
    private AccountServiceClient accountServiceClient;

    @MockitoBean
    private CustomerKycServiceClient customerKycServiceClient;

    @MockitoBean
    private LedgerServiceClient ledgerServiceClient;

    @MockitoBean
    private FraudRiskServiceClient fraudRiskServiceClient;

    private final UUID customerId = UUID.randomUUID();
    private final UUID sourceAccountId = UUID.randomUUID();
    private final UUID beneficiaryId = UUID.randomUUID();
    private final UUID destinationAccountId = UUID.randomUUID();
    private String customerToken;

    @BeforeEach
    void setUp() {
        customerToken = mintToken(customerId, "payer@example.com", "CUSTOMER");
        when(accountServiceClient.getAccount(sourceAccountId, customerToken)).thenReturn(Optional.of(
                new AccountServiceClient.AccountSummary(sourceAccountId, "ACTIVE", "USD",
                        new BigDecimal("5000.00"), new BigDecimal("20000.00"))));
        when(accountServiceClient.getBeneficiary(beneficiaryId, customerToken)).thenReturn(Optional.of(
                new AccountServiceClient.BeneficiarySummary(beneficiaryId, "ACTIVE", destinationAccountId, "ACTIVE")));
        when(customerKycServiceClient.hasVerifiedKyc(customerId, customerToken)).thenReturn(true);
        when(fraudRiskServiceClient.assess(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new FraudRiskServiceClient.RiskAssessmentResult(null, 0, "ALLOW", ""));
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

    private HttpEntity<CreatePaymentRequest> paymentEntity(String token, String idempotencyKey, BigDecimal amount) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (idempotencyKey != null) {
            headers.set("Idempotency-Key", idempotencyKey);
        }
        return new HttpEntity<>(new CreatePaymentRequest(sourceAccountId, beneficiaryId, amount, "USD", "test"), headers);
    }

    @Test
    void fullPaymentFlow_succeedsAndReplayReturnsSameTransaction() {
        String key = "key-" + UUID.randomUUID();

        ResponseEntity<PaymentResponse> first = restTemplate.exchange("/api/v1/payments", HttpMethod.POST,
                paymentEntity(customerToken, key, new BigDecimal("100.00")), PaymentResponse.class);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(first.getBody().status().name()).isEqualTo("SUCCESS");
        assertThat(first.getBody().destinationAccountId()).isEqualTo(destinationAccountId);
        UUID transactionId = first.getBody().id();

        ResponseEntity<PaymentResponse> replay = restTemplate.exchange("/api/v1/payments", HttpMethod.POST,
                paymentEntity(customerToken, key, new BigDecimal("100.00")), PaymentResponse.class);
        assertThat(replay.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(replay.getBody().id()).isEqualTo(transactionId);

        ResponseEntity<List> history = restTemplate.exchange("/api/v1/payments/{id}/status-history", HttpMethod.GET,
                new HttpEntity<>(bearerOnly(customerToken)), List.class, transactionId);
        assertThat(history.getBody()).isNotEmpty();
    }

    @Test
    void idempotencyKeyReuse_withDifferentAmount_isRejected() {
        String key = "key-" + UUID.randomUUID();
        restTemplate.exchange("/api/v1/payments", HttpMethod.POST,
                paymentEntity(customerToken, key, new BigDecimal("100.00")), PaymentResponse.class);

        ResponseEntity<ErrorResponse> reused = restTemplate.exchange("/api/v1/payments", HttpMethod.POST,
                paymentEntity(customerToken, key, new BigDecimal("200.00")), ErrorResponse.class);

        assertThat(reused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(reused.getBody().code()).isEqualTo("IDEMPOTENCY_KEY_REUSE");
    }

    @Test
    void missingIdempotencyKeyHeader_isRejected() {
        ResponseEntity<ErrorResponse> response = restTemplate.exchange("/api/v1/payments", HttpMethod.POST,
                paymentEntity(customerToken, null, new BigDecimal("100.00")), ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().code()).isEqualTo("MISSING_IDEMPOTENCY_KEY");
    }

    @Test
    void concurrentIdenticalRequests_neverProcessTwice() throws InterruptedException {
        String key = "key-" + UUID.randomUUID();
        int threadCount = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch go = new CountDownLatch(1);

        List<ResponseEntity<String>> responses;
        try {
            List<java.util.concurrent.Future<ResponseEntity<String>>> futures = IntStream.range(0, threadCount)
                    .mapToObj(i -> executor.submit(() -> {
                        ready.countDown();
                        go.await();
                        return restTemplate.exchange("/api/v1/payments", HttpMethod.POST,
                                paymentEntity(customerToken, key, new BigDecimal("50.00")), String.class);
                    }))
                    .collect(Collectors.toList());

            ready.await(5, TimeUnit.SECONDS);
            go.countDown();

            responses = futures.stream().map(f -> {
                try {
                    return f.get(10, TimeUnit.SECONDS);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }).collect(Collectors.toList());
        } finally {
            executor.shutdown();
        }

        Set<String> distinctTransactionIds = responses.stream()
                .filter(r -> r.getStatusCode() == HttpStatus.CREATED)
                .map(r -> {
                    try {
                        return new com.fasterxml.jackson.databind.ObjectMapper().readTree(r.getBody()).get("id").asText();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                })
                .collect(Collectors.toSet());

        // Whatever mix of 201s and 409s the race produced, every successful response must
        // reference the SAME transaction — proof that only one attempt was ever really processed.
        assertThat(distinctTransactionIds).hasSizeLessThanOrEqualTo(1);
        boolean allRecognized = responses.stream().allMatch(r ->
                r.getStatusCode() == HttpStatus.CREATED || r.getStatusCode() == HttpStatus.CONFLICT);
        assertThat(allRecognized).isTrue();
    }

    @Test
    void invalidSourceAccount_returnsFailedTransactionNotHttpError() {
        UUID unknownAccountId = UUID.randomUUID();
        when(accountServiceClient.getAccount(unknownAccountId, customerToken)).thenReturn(Optional.empty());

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(customerToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Idempotency-Key", "key-" + UUID.randomUUID());
        HttpEntity<CreatePaymentRequest> entity = new HttpEntity<>(
                new CreatePaymentRequest(unknownAccountId, beneficiaryId, new BigDecimal("10.00"), "USD", null), headers);

        ResponseEntity<PaymentResponse> response = restTemplate.exchange("/api/v1/payments", HttpMethod.POST,
                entity, PaymentResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().status().name()).isEqualTo("FAILED");
        assertThat(response.getBody().failureCode()).isEqualTo("SOURCE_ACCOUNT_INVALID");
    }

    @Test
    void successfulPayment_actuallyCallsLedgerServiceToPost() {
        String key = "key-" + UUID.randomUUID();
        when(ledgerServiceClient.post(any(), eq(sourceAccountId), eq(destinationAccountId),
                eq(new BigDecimal("40.00")), eq("USD"), any(), eq(customerToken)))
                .thenReturn(new LedgerServiceClient.PostingResult(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                        new BigDecimal("60.00"), new BigDecimal("40.00")));

        ResponseEntity<PaymentResponse> response = restTemplate.exchange("/api/v1/payments", HttpMethod.POST,
                paymentEntity(customerToken, key, new BigDecimal("40.00")), PaymentResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().status().name()).isEqualTo("SUCCESS");
        verify(ledgerServiceClient).post(eq(response.getBody().id()), eq(sourceAccountId), eq(destinationAccountId),
                eq(new BigDecimal("40.00")), eq("USD"), any(), eq(customerToken));
    }

    @Test
    void ledgerReportingInsufficientBalance_returnsFailedTransactionWithInsufficientBalanceCode() {
        String key = "key-" + UUID.randomUUID();
        when(ledgerServiceClient.post(any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new com.meridianbank.payment.client.InsufficientBalanceException());

        ResponseEntity<PaymentResponse> response = restTemplate.exchange("/api/v1/payments", HttpMethod.POST,
                paymentEntity(customerToken, key, new BigDecimal("40.00")), PaymentResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().status().name()).isEqualTo("FAILED");
        assertThat(response.getBody().failureCode()).isEqualTo("INSUFFICIENT_BALANCE");
    }

    @Test
    void fraudRiskServiceBlockDecision_returnsFailedTransactionWithFraudBlockedCode() {
        String key = "key-" + UUID.randomUUID();
        when(fraudRiskServiceClient.assess(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new FraudRiskServiceClient.RiskAssessmentResult(null, 90, "BLOCK", "RAPID_SEQUENTIAL_TRANSFERS"));

        ResponseEntity<PaymentResponse> response = restTemplate.exchange("/api/v1/payments", HttpMethod.POST,
                paymentEntity(customerToken, key, new BigDecimal("40.00")), PaymentResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().status().name()).isEqualTo("FAILED");
        assertThat(response.getBody().failureCode()).isEqualTo("FRAUD_BLOCKED");
        verify(ledgerServiceClient, org.mockito.Mockito.never()).post(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void customerCannotViewAnotherCustomersPayment() {
        String key = "key-" + UUID.randomUUID();
        PaymentResponse created = restTemplate.exchange("/api/v1/payments", HttpMethod.POST,
                paymentEntity(customerToken, key, new BigDecimal("25.00")), PaymentResponse.class).getBody();

        String attackerToken = mintToken(UUID.randomUUID(), "attacker@example.com", "CUSTOMER");
        ResponseEntity<ErrorResponse> response = restTemplate.exchange("/api/v1/payments/{id}", HttpMethod.GET,
                new HttpEntity<>(bearerOnly(attackerToken)), ErrorResponse.class, created.id());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    private HttpHeaders bearerOnly(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    /**
     * Maker-checker (docs/adr/0011-maker-checker.md): a REVIEW-flagged payment fails with
     * FRAUD_REVIEW_REQUIRED; a staff maker requests its release, a DIFFERENT staff checker must
     * approve, self-approval is rejected server-side, and a genuinely new transaction is created
     * and processed on approval — the original is left untouched.
     */
    @Test
    void releaseHeldPayment_requiresApprovalFromADifferentStaffMember() {
        String key = "key-" + UUID.randomUUID();
        when(fraudRiskServiceClient.assess(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new FraudRiskServiceClient.RiskAssessmentResult(null, 55, "REVIEW", "VELOCITY"));

        ResponseEntity<PaymentResponse> held = restTemplate.exchange("/api/v1/payments", HttpMethod.POST,
                paymentEntity(customerToken, key, new BigDecimal("60.00")), PaymentResponse.class);
        assertThat(held.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(held.getBody().status().name()).isEqualTo("FAILED");
        assertThat(held.getBody().failureCode()).isEqualTo("FRAUD_REVIEW_REQUIRED");
        UUID heldTransactionId = held.getBody().id();

        String makerToken = mintToken(UUID.randomUUID(), "maker@meridianbank.internal", "OPERATIONS");
        HttpHeaders makerHeaders = new HttpHeaders();
        makerHeaders.setBearerAuth(makerToken);
        makerHeaders.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<ApprovalRequestResponse> requestRelease = restTemplate.exchange(
                "/api/v1/payments/{id}/request-release", HttpMethod.POST,
                new HttpEntity<>(new RequestReleaseRequest("customer contacted and confirmed the transfer"), makerHeaders),
                ApprovalRequestResponse.class, heldTransactionId);
        assertThat(requestRelease.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(requestRelease.getBody().status()).isEqualTo("PENDING_APPROVAL");
        UUID approvalId = requestRelease.getBody().id();

        HttpHeaders makerJsonHeaders = new HttpHeaders();
        makerJsonHeaders.setBearerAuth(makerToken);
        makerJsonHeaders.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<ErrorResponse> selfApprove = restTemplate.exchange("/api/v1/approvals/{id}/approve",
                HttpMethod.PATCH, new HttpEntity<>(new ApprovalDecisionRequest(null), makerJsonHeaders),
                ErrorResponse.class, approvalId);
        assertThat(selfApprove.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(selfApprove.getBody().code()).isEqualTo("SELF_APPROVAL_NOT_ALLOWED");

        String checkerToken = mintToken(UUID.randomUUID(), "checker@meridianbank.internal", "COMPLIANCE_OFFICER");
        HttpHeaders checkerHeaders = new HttpHeaders();
        checkerHeaders.setBearerAuth(checkerToken);
        checkerHeaders.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<ApprovalRequestResponse> approved = restTemplate.exchange("/api/v1/approvals/{id}/approve",
                HttpMethod.PATCH, new HttpEntity<>(new ApprovalDecisionRequest("agreed, releasing"), checkerHeaders),
                ApprovalRequestResponse.class, approvalId);
        assertThat(approved.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(approved.getBody().status()).isEqualTo("APPROVED");
        UUID resultTransactionId = approved.getBody().resultTransactionId();
        assertThat(resultTransactionId).isNotNull().isNotEqualTo(heldTransactionId);

        ResponseEntity<PaymentResponse> resultTransaction = restTemplate.exchange("/api/v1/payments/{id}",
                HttpMethod.GET, new HttpEntity<>(bearerOnly(checkerToken)), PaymentResponse.class,
                resultTransactionId);
        assertThat(resultTransaction.getBody().status().name()).isEqualTo("SUCCESS");

        ResponseEntity<PaymentResponse> original = restTemplate.exchange("/api/v1/payments/{id}", HttpMethod.GET,
                new HttpEntity<>(bearerOnly(checkerToken)), PaymentResponse.class, heldTransactionId);
        assertThat(original.getBody().status().name()).isEqualTo("FAILED");
        assertThat(original.getBody().failureCode()).isEqualTo("FRAUD_REVIEW_REQUIRED");
    }

    @Test
    void releaseHeldPayment_rejectedApproval_leavesOriginalTransactionFailedAndCreatesNoNewOne() {
        String key = "key-" + UUID.randomUUID();
        when(fraudRiskServiceClient.assess(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new FraudRiskServiceClient.RiskAssessmentResult(null, 55, "REVIEW", "VELOCITY"));

        ResponseEntity<PaymentResponse> held = restTemplate.exchange("/api/v1/payments", HttpMethod.POST,
                paymentEntity(customerToken, key, new BigDecimal("60.00")), PaymentResponse.class);
        UUID heldTransactionId = held.getBody().id();

        String makerToken = mintToken(UUID.randomUUID(), "maker@meridianbank.internal", "OPERATIONS");
        HttpHeaders makerHeaders = new HttpHeaders();
        makerHeaders.setBearerAuth(makerToken);
        makerHeaders.setContentType(MediaType.APPLICATION_JSON);
        ApprovalRequestResponse requestRelease = restTemplate.exchange("/api/v1/payments/{id}/request-release",
                HttpMethod.POST, new HttpEntity<>(new RequestReleaseRequest("checking with customer"), makerHeaders),
                ApprovalRequestResponse.class, heldTransactionId).getBody();

        String checkerToken = mintToken(UUID.randomUUID(), "checker@meridianbank.internal", "RISK_ANALYST");
        HttpHeaders checkerHeaders = new HttpHeaders();
        checkerHeaders.setBearerAuth(checkerToken);
        checkerHeaders.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<ApprovalRequestResponse> rejected = restTemplate.exchange("/api/v1/approvals/{id}/reject",
                HttpMethod.PATCH, new HttpEntity<>(new ApprovalDecisionRequest("unable to reach customer"), checkerHeaders),
                ApprovalRequestResponse.class, requestRelease.id());
        assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(rejected.getBody().status()).isEqualTo("REJECTED");
        assertThat(rejected.getBody().resultTransactionId()).isNull();

        ResponseEntity<PaymentResponse> original = restTemplate.exchange("/api/v1/payments/{id}", HttpMethod.GET,
                new HttpEntity<>(bearerOnly(checkerToken)), PaymentResponse.class, heldTransactionId);
        assertThat(original.getBody().status().name()).isEqualTo("FAILED");
        assertThat(original.getBody().failureCode()).isEqualTo("FRAUD_REVIEW_REQUIRED");
    }

    /**
     * This service's auth boundary had no automated regression test at all before Phase 17 — see
     * docs/security/security-architecture.md, "Implementation status (Phase 17)".
     */
    @Test
    void protectedEndpoint_rejectsMissingGarbledAndExpiredTokens() {
        ResponseEntity<ErrorResponse> noToken = restTemplate.getForEntity("/api/v1/payments", ErrorResponse.class);
        assertThat(noToken.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        HttpHeaders garbageHeaders = new HttpHeaders();
        garbageHeaders.set(HttpHeaders.AUTHORIZATION, "Bearer not-a-real-jwt");
        ResponseEntity<ErrorResponse> garbledToken = restTemplate.exchange("/api/v1/payments",
                HttpMethod.GET, new HttpEntity<>(garbageHeaders), ErrorResponse.class);
        assertThat(garbledToken.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        Instant past = Instant.now().minusSeconds(3600);
        String expiredToken = Jwts.builder()
                .issuer("test")
                .subject(UUID.randomUUID().toString())
                .claim("email", "expired@example.com")
                .claim("role", "CUSTOMER")
                .issuedAt(Date.from(past.minusSeconds(900)))
                .expiration(Date.from(past))
                .signWith(SIGNING_KEY)
                .compact();
        ResponseEntity<ErrorResponse> expired = restTemplate.exchange("/api/v1/payments",
                HttpMethod.GET, new HttpEntity<>(bearerOnly(expiredToken)), ErrorResponse.class);
        assertThat(expired.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
