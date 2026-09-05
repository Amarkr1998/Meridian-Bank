package com.meridianbank.kyc.web;

import com.meridianbank.kyc.approval.ApprovalDecisionRequest;
import com.meridianbank.kyc.approval.ApprovalRequestResponse;
import com.meridianbank.kyc.client.AuthServiceClient;
import com.meridianbank.kyc.domain.DocumentType;
import com.meridianbank.kyc.web.dto.*;
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
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Full-stack coverage against real Postgres and Redis: registration (with auth-service stubbed
 * out — its own integration behavior is covered in auth-service's test suite), contact
 * verification, KYC submission, and the compliance review workflow, plus RBAC/resource-ownership
 * checks. See docs/architecture/onboarding-flow.md and docs/architecture/kyc-flow.md.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class CustomerKycControllerIntegrationTest {

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

    // Matches application.yml's local/demo default — this test never overrides MERIDIAN_JWT_SECRET.
    private static final String JWT_SECRET = "meridian-bank-local-demo-jwt-signing-secret-change-me-32bytes-min";
    private static final SecretKey SIGNING_KEY = Keys.hmacShaKeyFor(JWT_SECRET.getBytes(StandardCharsets.UTF_8));

    @Autowired
    private TestRestTemplate restTemplate;

    @MockitoBean
    private AuthServiceClient authServiceClient;

    @BeforeEach
    void stubAuthService() {
        when(authServiceClient.registerIdentity(anyString(), anyString())).thenAnswer(invocation ->
                new AuthServiceClient.CreatedIdentity(UUID.randomUUID(), invocation.getArgument(0), "CUSTOMER",
                        "ACTIVE", true, null, Instant.now()));
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

    private <T> HttpEntity<T> bearer(String token, T body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }

    private RegisterCustomerRequest sampleRegistration(String email) {
        return new RegisterCustomerRequest(email, "Correct-Horse1!", "Jane", "Doe",
                LocalDate.of(1990, 1, 1), "+1-555-0100", "1 Demo St", null, "Demo City", "DS",
                "00000", "Testland");
    }

    @Test
    void fullOnboardingFlow_registerVerifySubmitAndApproveKyc() {
        String email = "jane-" + UUID.randomUUID() + "@example.com";

        RegisterCustomerResponse registration = restTemplate.postForEntity(
                "/api/v1/customers/register", sampleRegistration(email), RegisterCustomerResponse.class).getBody();
        assertThat(registration).isNotNull();
        assertThat(registration.contactVerified()).isFalse();
        assertThat(registration.devOtp()).isNotBlank();

        UUID customerId = registration.customerId();
        String customerToken = mintToken(customerId, email, "CUSTOMER");

        ResponseEntity<Void> verifyResponse = restTemplate.postForEntity("/api/v1/customers/{id}/verify-contact",
                new VerifyContactRequest(registration.devOtp()), Void.class, customerId);
        assertThat(verifyResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<CustomerResponse> me = restTemplate.exchange("/api/v1/customers/{id}", HttpMethod.GET,
                bearer(customerToken), CustomerResponse.class, customerId);
        assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(me.getBody().contactVerified()).isTrue();

        SubmitKycRequest kycRequest = new SubmitKycRequest("Testland", "Engineer",
                List.of(new DocumentEntry(DocumentType.NATIONAL_ID, "REF-12345")));
        ResponseEntity<KycRecordResponse> submitted = restTemplate.exchange("/api/v1/customers/{id}/kyc",
                HttpMethod.POST, bearer(customerToken, kycRequest), KycRecordResponse.class, customerId);
        assertThat(submitted.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(submitted.getBody().status().name()).isEqualTo("KYC_PENDING");
        assertThat(submitted.getBody().documents()).hasSize(1);

        UUID kycId = submitted.getBody().id();
        String complianceToken = mintToken(UUID.randomUUID(), "compliance@meridianbank.local", "COMPLIANCE_OFFICER");

        ResponseEntity<KycRecordResponse> started = restTemplate.exchange("/api/v1/kyc/{id}/start-review",
                HttpMethod.POST, bearer(complianceToken), KycRecordResponse.class, kycId);
        assertThat(started.getBody().status().name()).isEqualTo("KYC_IN_REVIEW");

        ResponseEntity<KycRecordResponse> approved = restTemplate.exchange("/api/v1/kyc/{id}/approve",
                HttpMethod.POST, bearer(complianceToken), KycRecordResponse.class, kycId);
        assertThat(approved.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(approved.getBody().status().name()).isEqualTo("KYC_VERIFIED");
        assertThat(approved.getBody().reviewedBy()).isNotNull();
    }

    @Test
    void customerCannotReadAnotherCustomersProfile() {
        String email = "victim-" + UUID.randomUUID() + "@example.com";
        RegisterCustomerResponse registration = restTemplate.postForEntity(
                "/api/v1/customers/register", sampleRegistration(email), RegisterCustomerResponse.class).getBody();

        String attackerToken = mintToken(UUID.randomUUID(), "attacker@example.com", "CUSTOMER");
        ResponseEntity<ErrorResponse> response = restTemplate.exchange("/api/v1/customers/{id}", HttpMethod.GET,
                bearer(attackerToken), ErrorResponse.class, registration.customerId());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().code()).isEqualTo("ACCESS_DENIED");
    }

    @Test
    void customerCannotAccessKycReviewQueue() {
        String customerToken = mintToken(UUID.randomUUID(), "customer@example.com", "CUSTOMER");

        ResponseEntity<ErrorResponse> response = restTemplate.exchange("/api/v1/kyc", HttpMethod.GET,
                bearer(customerToken), ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void submittingKycBeforeContactVerification_isRejected() {
        String email = "unverified-" + UUID.randomUUID() + "@example.com";
        RegisterCustomerResponse registration = restTemplate.postForEntity(
                "/api/v1/customers/register", sampleRegistration(email), RegisterCustomerResponse.class).getBody();
        String customerToken = mintToken(registration.customerId(), email, "CUSTOMER");

        SubmitKycRequest kycRequest = new SubmitKycRequest("Testland", "Engineer",
                List.of(new DocumentEntry(DocumentType.NATIONAL_ID, "REF-999")));
        ResponseEntity<ErrorResponse> response = restTemplate.exchange("/api/v1/customers/{id}/kyc",
                HttpMethod.POST, bearer(customerToken, kycRequest), ErrorResponse.class, registration.customerId());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().code()).isEqualTo("CONTACT_NOT_VERIFIED");
    }

    @Test
    void customerStatusChange_requiresApprovalFromADifferentStaffMember() {
        String email = "status-" + UUID.randomUUID() + "@example.com";
        RegisterCustomerResponse registration = restTemplate.postForEntity(
                "/api/v1/customers/register", sampleRegistration(email), RegisterCustomerResponse.class).getBody();
        UUID customerId = registration.customerId();

        String makerToken = mintToken(UUID.randomUUID(), "maker@meridianbank.local", "OPERATIONS");
        ResponseEntity<ApprovalRequestResponse> created = restTemplate.exchange("/api/v1/customers/{id}/status",
                HttpMethod.PATCH,
                bearer(makerToken, new UpdateCustomerStatusRequest(
                        com.meridianbank.kyc.domain.CustomerStatus.BLOCKED, "suspected fraud")),
                ApprovalRequestResponse.class, customerId);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(created.getBody().status()).isEqualTo("PENDING_APPROVAL");
        UUID approvalId = created.getBody().id();

        ResponseEntity<CustomerResponse> stillActive = restTemplate.exchange("/api/v1/customers/{id}",
                HttpMethod.GET, bearer(makerToken), CustomerResponse.class, customerId);
        assertThat(stillActive.getBody().status().name()).isEqualTo("ACTIVE");

        ResponseEntity<ErrorResponse> selfApproval = restTemplate.exchange("/api/v1/approvals/{id}/approve",
                HttpMethod.PATCH, bearer(makerToken), ErrorResponse.class, approvalId);
        assertThat(selfApproval.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(selfApproval.getBody().code()).isEqualTo("SELF_APPROVAL_NOT_ALLOWED");

        String checkerToken = mintToken(UUID.randomUUID(), "checker@meridianbank.local", "ADMIN");
        ResponseEntity<ApprovalRequestResponse> decided = restTemplate.exchange("/api/v1/approvals/{id}/approve",
                HttpMethod.PATCH, bearer(checkerToken, new ApprovalDecisionRequest("confirmed")),
                ApprovalRequestResponse.class, approvalId);
        assertThat(decided.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(decided.getBody().status()).isEqualTo("APPROVED");

        ResponseEntity<CustomerResponse> nowBlocked = restTemplate.exchange("/api/v1/customers/{id}",
                HttpMethod.GET, bearer(makerToken), CustomerResponse.class, customerId);
        assertThat(nowBlocked.getBody().status().name()).isEqualTo("BLOCKED");
    }

    /**
     * This service's auth boundary had no automated regression test at all before Phase 17 —
     * every other auth-rejection scenario tested here (self-approval, cross-customer 403) assumed
     * a request with SOME valid token; nothing proved a request with no token, a garbage token, or
     * an expired-but-otherwise-valid token is actually rejected. See docs/security/security-architecture.md,
     * "Implementation status (Phase 17)".
     */
    @Test
    void protectedEndpoint_rejectsMissingGarbledAndExpiredTokens() {
        UUID someCustomerId = UUID.randomUUID();

        ResponseEntity<ErrorResponse> noToken = restTemplate.getForEntity(
                "/api/v1/customers/{id}", ErrorResponse.class, someCustomerId);
        assertThat(noToken.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        HttpHeaders garbageHeaders = new HttpHeaders();
        garbageHeaders.set(HttpHeaders.AUTHORIZATION, "Bearer not-a-real-jwt");
        ResponseEntity<ErrorResponse> garbledToken = restTemplate.exchange("/api/v1/customers/{id}",
                HttpMethod.GET, new HttpEntity<>(garbageHeaders), ErrorResponse.class, someCustomerId);
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
        ResponseEntity<ErrorResponse> expired = restTemplate.exchange("/api/v1/customers/{id}",
                HttpMethod.GET, bearer(expiredToken), ErrorResponse.class, someCustomerId);
        assertThat(expired.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
