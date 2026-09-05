package com.meridianbank.account.web;

import com.meridianbank.account.approval.ApprovalDecisionRequest;
import com.meridianbank.account.approval.ApprovalRequestResponse;
import com.meridianbank.account.client.CustomerKycServiceClient;
import com.meridianbank.account.client.LedgerServiceClient;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.crypto.SecretKey;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Full-stack coverage against real Postgres, with CustomerKycServiceClient stubbed via
 * @MockitoBean (customer-kyc-service's own behavior is covered in its own test suite). Covers the
 * complete submit → start-review → approve → account lifecycle flow, plus resource-ownership and
 * RBAC checks. See docs/architecture/onboarding-flow.md.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class AccountControllerIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    // Matches application.yml's local/demo default — this test never overrides MERIDIAN_JWT_SECRET.
    private static final String JWT_SECRET = "meridian-bank-local-demo-jwt-signing-secret-change-me-32bytes-min";
    private static final SecretKey SIGNING_KEY = Keys.hmacShaKeyFor(JWT_SECRET.getBytes(StandardCharsets.UTF_8));

    @Autowired
    private TestRestTemplate restTemplate;

    @MockitoBean
    private CustomerKycServiceClient customerKycServiceClient;

    @MockitoBean
    private LedgerServiceClient ledgerServiceClient;

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
    void fullAccountOpeningFlow_submitReviewApproveThenLifecycle() {
        UUID customerId = UUID.randomUUID();
        String customerToken = mintToken(customerId, "jane@example.com", "CUSTOMER");
        when(customerKycServiceClient.hasVerifiedKyc(customerId, customerToken)).thenReturn(true);

        ResponseEntity<AccountRequestResponse> submitted = restTemplate.exchange("/api/v1/accounts/requests",
                HttpMethod.POST, bearer(customerToken, new CreateAccountRequest(AccountType.SAVINGS)),
                AccountRequestResponse.class);
        assertThat(submitted.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(submitted.getBody().status().name()).isEqualTo("ACCOUNT_REQUESTED");
        UUID requestId = submitted.getBody().id();

        String opsToken = mintToken(UUID.randomUUID(), "ops@meridianbank.local", "OPERATIONS");
        ResponseEntity<AccountRequestResponse> started = restTemplate.exchange(
                "/api/v1/accounts/requests/{id}/start-review", HttpMethod.POST, bearer(opsToken),
                AccountRequestResponse.class, requestId);
        assertThat(started.getBody().status().name()).isEqualTo("UNDER_REVIEW");

        ResponseEntity<AccountRequestResponse> approved = restTemplate.exchange(
                "/api/v1/accounts/requests/{id}/approve", HttpMethod.POST, bearer(opsToken),
                AccountRequestResponse.class, requestId);
        assertThat(approved.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(approved.getBody().status().name()).isEqualTo("APPROVED");
        UUID accountId = approved.getBody().accountId();
        assertThat(accountId).isNotNull();

        when(ledgerServiceClient.getBalance(accountId, customerToken)).thenReturn(
                Optional.of(new LedgerServiceClient.BalanceSummary(new BigDecimal("1234.56"), new BigDecimal("1234.56"), "USD")));

        ResponseEntity<AccountResponse> account = restTemplate.exchange("/api/v1/accounts/{id}", HttpMethod.GET,
                bearer(customerToken), AccountResponse.class, accountId);
        assertThat(account.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(account.getBody().status().name()).isEqualTo("ACTIVE");
        assertThat(account.getBody().maskedAccountNumber()).startsWith("******");
        assertThat(account.getBody().maskedAccountNumber()).hasSize(10);
        assertThat(account.getBody().availableBalance()).isEqualByComparingTo("1234.56");

        ResponseEntity<AccountResponse> frozen = restTemplate.exchange("/api/v1/accounts/{id}/freeze",
                HttpMethod.PATCH, bearer(opsToken, new AccountStatusChangeRequest("routine check")),
                AccountResponse.class, accountId);
        assertThat(frozen.getBody().status().name()).isEqualTo("FROZEN");

        ResponseEntity<AccountResponse> unfrozen = restTemplate.exchange("/api/v1/accounts/{id}/unfreeze",
                HttpMethod.PATCH, bearer(opsToken, new AccountStatusChangeRequest("check complete")),
                AccountResponse.class, accountId);
        assertThat(unfrozen.getBody().status().name()).isEqualTo("ACTIVE");
    }

    @Test
    void submittingWithoutVerifiedKyc_isRejected() {
        UUID customerId = UUID.randomUUID();
        String customerToken = mintToken(customerId, "unverified@example.com", "CUSTOMER");
        when(customerKycServiceClient.hasVerifiedKyc(customerId, customerToken)).thenReturn(false);

        ResponseEntity<ErrorResponse> response = restTemplate.exchange("/api/v1/accounts/requests",
                HttpMethod.POST, bearer(customerToken, new CreateAccountRequest(AccountType.SAVINGS)),
                ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().code()).isEqualTo("KYC_NOT_VERIFIED");
    }

    @Test
    void customerCannotFreezeAnAccount() {
        UUID customerId = UUID.randomUUID();
        String customerToken = mintToken(customerId, "jane2@example.com", "CUSTOMER");
        when(customerKycServiceClient.hasVerifiedKyc(customerId, customerToken)).thenReturn(true);

        AccountRequestResponse submitted = restTemplate.exchange("/api/v1/accounts/requests", HttpMethod.POST,
                bearer(customerToken, new CreateAccountRequest(AccountType.CURRENT)),
                AccountRequestResponse.class).getBody();
        String opsToken = mintToken(UUID.randomUUID(), "ops2@meridianbank.local", "OPERATIONS");
        restTemplate.exchange("/api/v1/accounts/requests/{id}/start-review", HttpMethod.POST, bearer(opsToken),
                AccountRequestResponse.class, submitted.id());
        AccountRequestResponse approved = restTemplate.exchange("/api/v1/accounts/requests/{id}/approve",
                HttpMethod.POST, bearer(opsToken), AccountRequestResponse.class, submitted.id()).getBody();

        ResponseEntity<ErrorResponse> response = restTemplate.exchange("/api/v1/accounts/{id}/freeze",
                HttpMethod.PATCH, bearer(customerToken, new AccountStatusChangeRequest("nope")),
                ErrorResponse.class, approved.accountId());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void customerCannotViewAnotherCustomersAccount() {
        UUID ownerId = UUID.randomUUID();
        String ownerToken = mintToken(ownerId, "owner@example.com", "CUSTOMER");
        when(customerKycServiceClient.hasVerifiedKyc(ownerId, ownerToken)).thenReturn(true);

        AccountRequestResponse submitted = restTemplate.exchange("/api/v1/accounts/requests", HttpMethod.POST,
                bearer(ownerToken, new CreateAccountRequest(AccountType.SAVINGS)),
                AccountRequestResponse.class).getBody();
        String opsToken = mintToken(UUID.randomUUID(), "ops3@meridianbank.local", "OPERATIONS");
        restTemplate.exchange("/api/v1/accounts/requests/{id}/start-review", HttpMethod.POST, bearer(opsToken),
                AccountRequestResponse.class, submitted.id());
        AccountRequestResponse approved = restTemplate.exchange("/api/v1/accounts/requests/{id}/approve",
                HttpMethod.POST, bearer(opsToken), AccountRequestResponse.class, submitted.id()).getBody();

        String attackerToken = mintToken(UUID.randomUUID(), "attacker@example.com", "CUSTOMER");
        ResponseEntity<ErrorResponse> response = restTemplate.exchange("/api/v1/accounts/{id}", HttpMethod.GET,
                bearer(attackerToken), ErrorResponse.class, approved.accountId());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void accountViewing_survivesLedgerServiceBeingUnreachable() {
        UUID customerId = UUID.randomUUID();
        String customerToken = mintToken(customerId, "jane4@example.com", "CUSTOMER");
        when(customerKycServiceClient.hasVerifiedKyc(customerId, customerToken)).thenReturn(true);
        // Deliberately not stubbing ledgerServiceClient — an unstubbed mock returning
        // Optional.empty() (Mockito's default for Optional-returning methods) stands in for
        // ledger-service being unreachable, exercising the same code path as a real failure
        // reaching LedgerServiceClient's fallback (see its class-level Javadoc).

        AccountRequestResponse submitted = restTemplate.exchange("/api/v1/accounts/requests", HttpMethod.POST,
                bearer(customerToken, new CreateAccountRequest(AccountType.SAVINGS)),
                AccountRequestResponse.class).getBody();
        String opsToken = mintToken(UUID.randomUUID(), "ops4@meridianbank.local", "OPERATIONS");
        restTemplate.exchange("/api/v1/accounts/requests/{id}/start-review", HttpMethod.POST, bearer(opsToken),
                AccountRequestResponse.class, submitted.id());
        AccountRequestResponse approved = restTemplate.exchange("/api/v1/accounts/requests/{id}/approve",
                HttpMethod.POST, bearer(opsToken), AccountRequestResponse.class, submitted.id()).getBody();

        ResponseEntity<AccountResponse> account = restTemplate.exchange("/api/v1/accounts/{id}", HttpMethod.GET,
                bearer(customerToken), AccountResponse.class, approved.accountId());

        assertThat(account.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(account.getBody().status().name()).isEqualTo("ACTIVE");
        assertThat(account.getBody().availableBalance()).isNull();
    }

    @Test
    void blockAccount_requiresApprovalFromADifferentStaffMember() {
        UUID customerId = UUID.randomUUID();
        String customerToken = mintToken(customerId, "jane5@example.com", "CUSTOMER");
        when(customerKycServiceClient.hasVerifiedKyc(customerId, customerToken)).thenReturn(true);

        AccountRequestResponse submitted = restTemplate.exchange("/api/v1/accounts/requests", HttpMethod.POST,
                bearer(customerToken, new CreateAccountRequest(AccountType.SAVINGS)),
                AccountRequestResponse.class).getBody();
        String opsToken = mintToken(UUID.randomUUID(), "ops5@meridianbank.local", "OPERATIONS");
        restTemplate.exchange("/api/v1/accounts/requests/{id}/start-review", HttpMethod.POST, bearer(opsToken),
                AccountRequestResponse.class, submitted.id());
        AccountRequestResponse approved = restTemplate.exchange("/api/v1/accounts/requests/{id}/approve",
                HttpMethod.POST, bearer(opsToken), AccountRequestResponse.class, submitted.id()).getBody();
        UUID accountId = approved.accountId();

        // Maker: a RISK_ANALYST-adjacent OPERATIONS staffer requests the block — this does NOT
        // block the account immediately (docs/adr/0011-maker-checker.md).
        String makerToken = mintToken(UUID.randomUUID(), "maker@meridianbank.local", "OPERATIONS");
        ResponseEntity<ApprovalRequestResponse> created = restTemplate.exchange("/api/v1/accounts/{id}/block",
                HttpMethod.PATCH, bearer(makerToken, new AccountStatusChangeRequest("suspected fraud")),
                ApprovalRequestResponse.class, accountId);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(created.getBody().status()).isEqualTo("PENDING_APPROVAL");
        UUID approvalId = created.getBody().id();

        ResponseEntity<AccountResponse> stillActive = restTemplate.exchange("/api/v1/accounts/{id}", HttpMethod.GET,
                bearer(customerToken), AccountResponse.class, accountId);
        assertThat(stillActive.getBody().status().name()).isEqualTo("ACTIVE");

        // The maker cannot approve their own request — enforced server-side, not just hidden in a UI.
        ResponseEntity<ErrorResponse> selfApproval = restTemplate.exchange("/api/v1/approvals/{id}/approve",
                HttpMethod.PATCH, bearer(makerToken), ErrorResponse.class, approvalId);
        assertThat(selfApproval.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(selfApproval.getBody().code()).isEqualTo("SELF_APPROVAL_NOT_ALLOWED");

        // A different staff member (the checker) approves — only now does the block execute.
        String checkerToken = mintToken(UUID.randomUUID(), "checker@meridianbank.local", "ADMIN");
        ResponseEntity<ApprovalRequestResponse> decided = restTemplate.exchange("/api/v1/approvals/{id}/approve",
                HttpMethod.PATCH, bearer(checkerToken, new ApprovalDecisionRequest("confirmed fraudulent activity")),
                ApprovalRequestResponse.class, approvalId);
        assertThat(decided.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(decided.getBody().status()).isEqualTo("APPROVED");

        ResponseEntity<AccountResponse> nowBlocked = restTemplate.exchange("/api/v1/accounts/{id}", HttpMethod.GET,
                bearer(customerToken), AccountResponse.class, accountId);
        assertThat(nowBlocked.getBody().status().name()).isEqualTo("BLOCKED");
    }

    @Test
    void blockAccount_rejectedApproval_leavesAccountActive() {
        UUID customerId = UUID.randomUUID();
        String customerToken = mintToken(customerId, "jane6@example.com", "CUSTOMER");
        when(customerKycServiceClient.hasVerifiedKyc(customerId, customerToken)).thenReturn(true);

        AccountRequestResponse submitted = restTemplate.exchange("/api/v1/accounts/requests", HttpMethod.POST,
                bearer(customerToken, new CreateAccountRequest(AccountType.SAVINGS)),
                AccountRequestResponse.class).getBody();
        String opsToken = mintToken(UUID.randomUUID(), "ops6@meridianbank.local", "OPERATIONS");
        restTemplate.exchange("/api/v1/accounts/requests/{id}/start-review", HttpMethod.POST, bearer(opsToken),
                AccountRequestResponse.class, submitted.id());
        AccountRequestResponse approved = restTemplate.exchange("/api/v1/accounts/requests/{id}/approve",
                HttpMethod.POST, bearer(opsToken), AccountRequestResponse.class, submitted.id()).getBody();
        UUID accountId = approved.accountId();

        String makerToken = mintToken(UUID.randomUUID(), "maker2@meridianbank.local", "OPERATIONS");
        ApprovalRequestResponse created = restTemplate.exchange("/api/v1/accounts/{id}/block", HttpMethod.PATCH,
                bearer(makerToken, new AccountStatusChangeRequest("possible fraud")),
                ApprovalRequestResponse.class, accountId).getBody();

        String checkerToken = mintToken(UUID.randomUUID(), "checker2@meridianbank.local", "ADMIN");
        ResponseEntity<ApprovalRequestResponse> rejected = restTemplate.exchange("/api/v1/approvals/{id}/reject",
                HttpMethod.PATCH, bearer(checkerToken, new ApprovalDecisionRequest("insufficient evidence")),
                ApprovalRequestResponse.class, created.id());
        assertThat(rejected.getBody().status()).isEqualTo("REJECTED");

        ResponseEntity<AccountResponse> stillActive = restTemplate.exchange("/api/v1/accounts/{id}", HttpMethod.GET,
                bearer(customerToken), AccountResponse.class, accountId);
        assertThat(stillActive.getBody().status().name()).isEqualTo("ACTIVE");
    }

    /**
     * This service's auth boundary had no automated regression test at all before Phase 17 — see
     * docs/security/security-architecture.md, "Implementation status (Phase 17)".
     */
    @Test
    void protectedEndpoint_rejectsMissingGarbledAndExpiredTokens() {
        ResponseEntity<ErrorResponse> noToken = restTemplate.getForEntity("/api/v1/accounts", ErrorResponse.class);
        assertThat(noToken.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        HttpHeaders garbageHeaders = new HttpHeaders();
        garbageHeaders.set(HttpHeaders.AUTHORIZATION, "Bearer not-a-real-jwt");
        ResponseEntity<ErrorResponse> garbledToken = restTemplate.exchange("/api/v1/accounts",
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
        ResponseEntity<ErrorResponse> expired = restTemplate.exchange("/api/v1/accounts",
                HttpMethod.GET, bearer(expiredToken), ErrorResponse.class);
        assertThat(expired.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
