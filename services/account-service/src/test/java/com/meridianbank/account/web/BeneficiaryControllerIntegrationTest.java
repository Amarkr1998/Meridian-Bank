package com.meridianbank.account.web;

import com.meridianbank.account.client.CustomerKycServiceClient;
import com.meridianbank.account.domain.AccountType;
import com.meridianbank.account.web.dto.*;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
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
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Full-stack coverage against real Postgres + Redis. CustomerKycServiceClient is stubbed via
 * @MockitoBean so an account can be opened as test fixture setup without a live
 * customer-kyc-service. Covers add → verify → deactivate → reactivate, staff block/unblock, and
 * the account-existence / self-beneficiary / duplicate validations. See account-service/README.md.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class BeneficiaryControllerIntegrationTest {

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
    private CustomerKycServiceClient customerKycServiceClient;

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

    private <T> HttpEntity<T> bearer(String token, T body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }

    private HttpEntity<Void> bearer(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return new HttpEntity<>(headers);
    }

    @Test
    void fullBeneficiaryFlow_addVerifyDeactivateReactivate() {
        UUID ownerId = UUID.randomUUID();
        String ownerToken = mintToken(ownerId, "owner@example.com", "CUSTOMER");
        when(customerKycServiceClient.hasVerifiedKyc(ownerId, ownerToken)).thenReturn(true);
        String rawAccountNumber = openActiveAccountAndReturnNumber(ownerId, ownerToken);

        UUID senderId = UUID.randomUUID();
        String senderToken = mintToken(senderId, "sender@example.com", "CUSTOMER");

        ResponseEntity<AddBeneficiaryResponse> added = restTemplate.exchange("/api/v1/beneficiaries", HttpMethod.POST,
                bearer(senderToken, new AddBeneficiaryRequest("My Friend", "Account Owner", rawAccountNumber)),
                AddBeneficiaryResponse.class);
        assertThat(added.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(added.getBody().beneficiary().status().name()).isEqualTo("PENDING");
        assertThat(added.getBody().devOtp()).isNotBlank();
        UUID beneficiaryId = added.getBody().beneficiary().id();

        ResponseEntity<BeneficiaryResponse> verified = restTemplate.exchange("/api/v1/beneficiaries/{id}/verify",
                HttpMethod.POST, bearer(senderToken, new VerifyBeneficiaryRequest(added.getBody().devOtp())),
                BeneficiaryResponse.class, beneficiaryId);
        assertThat(verified.getBody().status().name()).isEqualTo("ACTIVE");
        assertThat(verified.getBody().activatedAt()).isNotNull();

        ResponseEntity<BeneficiaryResponse> deactivated = restTemplate.exchange("/api/v1/beneficiaries/{id}/deactivate",
                HttpMethod.PATCH, bearer(senderToken), BeneficiaryResponse.class, beneficiaryId);
        assertThat(deactivated.getBody().status().name()).isEqualTo("INACTIVE");

        ResponseEntity<BeneficiaryResponse> reactivated = restTemplate.exchange("/api/v1/beneficiaries/{id}/activate",
                HttpMethod.PATCH, bearer(senderToken), BeneficiaryResponse.class, beneficiaryId);
        assertThat(reactivated.getBody().status().name()).isEqualTo("ACTIVE");
    }

    @Test
    void addingBeneficiary_withNonExistentAccountNumber_isRejected() {
        UUID senderId = UUID.randomUUID();
        String senderToken = mintToken(senderId, "sender2@example.com", "CUSTOMER");

        ResponseEntity<ErrorResponse> response = restTemplate.exchange("/api/v1/beneficiaries", HttpMethod.POST,
                bearer(senderToken, new AddBeneficiaryRequest("Nobody", "Nobody", "0000000000")), ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().code()).isEqualTo("INVALID_BENEFICIARY_ACCOUNT");
    }

    @Test
    void addingOwnAccountAsBeneficiary_isRejected() {
        UUID ownerId = UUID.randomUUID();
        String ownerToken = mintToken(ownerId, "self@example.com", "CUSTOMER");
        when(customerKycServiceClient.hasVerifiedKyc(ownerId, ownerToken)).thenReturn(true);
        String rawAccountNumber = openActiveAccountAndReturnNumber(ownerId, ownerToken);

        ResponseEntity<ErrorResponse> response = restTemplate.exchange("/api/v1/beneficiaries", HttpMethod.POST,
                bearer(ownerToken, new AddBeneficiaryRequest("Myself", "Me", rawAccountNumber)), ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().code()).isEqualTo("SELF_BENEFICIARY_NOT_ALLOWED");
    }

    @Test
    void staffCanBlockAndUnblockABeneficiary() {
        UUID ownerId = UUID.randomUUID();
        String ownerToken = mintToken(ownerId, "owner2@example.com", "CUSTOMER");
        when(customerKycServiceClient.hasVerifiedKyc(ownerId, ownerToken)).thenReturn(true);
        String rawAccountNumber = openActiveAccountAndReturnNumber(ownerId, ownerToken);

        UUID senderId = UUID.randomUUID();
        String senderToken = mintToken(senderId, "sender3@example.com", "CUSTOMER");
        AddBeneficiaryResponse added = restTemplate.exchange("/api/v1/beneficiaries", HttpMethod.POST,
                bearer(senderToken, new AddBeneficiaryRequest("Friend", "Owner", rawAccountNumber)),
                AddBeneficiaryResponse.class).getBody();

        String riskToken = mintToken(UUID.randomUUID(), "risk@meridianbank.local", "RISK_ANALYST");
        ResponseEntity<BeneficiaryResponse> blocked = restTemplate.exchange("/api/v1/beneficiaries/{id}/block",
                HttpMethod.PATCH, bearer(riskToken, new BeneficiaryStatusChangeRequest("suspected fraud ring")),
                BeneficiaryResponse.class, added.beneficiary().id());
        assertThat(blocked.getBody().status().name()).isEqualTo("BLOCKED");

        // The owner cannot self-deactivate a blocked beneficiary — only staff can move it.
        ResponseEntity<ErrorResponse> selfAttempt = restTemplate.exchange("/api/v1/beneficiaries/{id}/deactivate",
                HttpMethod.PATCH, bearer(senderToken), ErrorResponse.class, added.beneficiary().id());
        assertThat(selfAttempt.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        ResponseEntity<BeneficiaryResponse> unblocked = restTemplate.exchange("/api/v1/beneficiaries/{id}/unblock",
                HttpMethod.PATCH, bearer(riskToken, new BeneficiaryStatusChangeRequest("cleared")),
                BeneficiaryResponse.class, added.beneficiary().id());
        assertThat(unblocked.getBody().status().name()).isEqualTo("INACTIVE");
    }

    @Test
    void customerCannotViewAnotherCustomersBeneficiary() {
        UUID ownerId = UUID.randomUUID();
        String ownerToken = mintToken(ownerId, "owner3@example.com", "CUSTOMER");
        when(customerKycServiceClient.hasVerifiedKyc(ownerId, ownerToken)).thenReturn(true);
        String rawAccountNumber = openActiveAccountAndReturnNumber(ownerId, ownerToken);

        UUID senderId = UUID.randomUUID();
        String senderToken = mintToken(senderId, "sender4@example.com", "CUSTOMER");
        AddBeneficiaryResponse added = restTemplate.exchange("/api/v1/beneficiaries", HttpMethod.POST,
                bearer(senderToken, new AddBeneficiaryRequest("Friend", "Owner", rawAccountNumber)),
                AddBeneficiaryResponse.class).getBody();

        String attackerToken = mintToken(UUID.randomUUID(), "attacker@example.com", "CUSTOMER");
        ResponseEntity<ErrorResponse> response = restTemplate.exchange("/api/v1/beneficiaries/{id}", HttpMethod.GET,
                bearer(attackerToken), ErrorResponse.class, added.beneficiary().id());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    /**
     * Opens and approves a real ACTIVE account for the given customer (same request/approve flow
     * as AccountControllerIntegrationTest), then returns its raw account number. The public API
     * never exposes the unmasked number (see AccountResponse), so {@link TestAccountNumberLookup}
     * — a test-only bean reading it straight from the repository — recovers it for test setup.
     */
    private String openActiveAccountAndReturnNumber(UUID customerId, String token) {
        AccountRequestResponse submitted = restTemplate.exchange("/api/v1/accounts/requests", HttpMethod.POST,
                bearer(token, new CreateAccountRequest(AccountType.SAVINGS)), AccountRequestResponse.class).getBody();
        String opsToken = mintToken(UUID.randomUUID(), "ops-" + UUID.randomUUID() + "@meridianbank.local", "OPERATIONS");
        restTemplate.exchange("/api/v1/accounts/requests/{id}/start-review", HttpMethod.POST, bearer(opsToken),
                AccountRequestResponse.class, submitted.id());
        AccountRequestResponse approved = restTemplate.exchange("/api/v1/accounts/requests/{id}/approve",
                HttpMethod.POST, bearer(opsToken), AccountRequestResponse.class, submitted.id()).getBody();
        return accountNumberLookup.rawNumberFor(approved.accountId());
    }

    @Autowired
    private TestAccountNumberLookup accountNumberLookup;
}
