package com.meridianbank.ledger.web;

import com.meridianbank.ledger.domain.Balance;
import com.meridianbank.ledger.reconciliation.ReconciliationService;
import com.meridianbank.ledger.repository.BalanceRepository;
import com.meridianbank.ledger.service.LedgerPostingService;
import com.meridianbank.ledger.web.dto.ResolutionRequest;
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
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Full-stack RBAC + workflow coverage against real Postgres. The comparison engine itself
 *  (matched/mismatched/pending classification) is covered by ReconciliationIntegrationTest — this
 *  test focuses on the HTTP contract and the investigate -> resolve staff workflow. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class ReconciliationControllerIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final String JWT_SECRET = "meridian-bank-local-demo-jwt-signing-secret-change-me-32bytes-min";
    private static final SecretKey SIGNING_KEY = Keys.hmacShaKeyFor(JWT_SECRET.getBytes(StandardCharsets.UTF_8));

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private LedgerPostingService postingService;

    @Autowired
    private BalanceRepository balanceRepository;

    @Autowired
    private ReconciliationService reconciliationService;

    private void seedBalance(UUID accountId, String amount) {
        Balance balance = new Balance(accountId, "USD");
        balance.credit(new BigDecimal(amount));
        balanceRepository.save(balance);
    }

    private String mintToken(String role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer("test")
                .subject(UUID.randomUUID().toString())
                .claim("email", "staff@meridianbank.local")
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

    /** A transactionId whose least-significant-bits mod 10 == 9 — see ExternalFeedGenerator's
     *  Javadoc: this always synthesizes an immediately-available, deliberately wrong amount. */
    private UUID mismatchTransactionId() {
        return new UUID(UUID.randomUUID().getMostSignificantBits(), 9);
    }

    private UUID postAMismatchingTransaction() {
        UUID debitAccount = UUID.randomUUID();
        UUID creditAccount = UUID.randomUUID();
        seedBalance(debitAccount, "1000.00");
        UUID txn = mismatchTransactionId();
        postingService.post(txn, debitAccount, creditAccount, new BigDecimal("60.00"), "USD", "controller-test");
        return txn;
    }

    @Test
    void operationsStaff_canListAndReadReconciliationRecords() {
        postAMismatchingTransaction();
        reconciliationService.run();
        String token = mintToken("OPERATIONS");

        ResponseEntity<Map> response = restTemplate.exchange("/api/v1/ledger/reconciliation-records?status=MISMATCHED",
                HttpMethod.GET, bearer(token), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((java.util.List<?>) response.getBody().get("content")).isNotEmpty();
    }

    @Test
    void customerToken_isRejectedWith403() {
        String token = mintToken("CUSTOMER");

        ResponseEntity<Map> response = restTemplate.exchange("/api/v1/ledger/reconciliation-records",
                HttpMethod.GET, bearer(token), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void auditorToken_canViewButCannotStartInvestigation() {
        UUID txn = postAMismatchingTransaction();
        reconciliationService.run();
        UUID recordId = reconciliationService.queue(null, org.springframework.data.domain.Pageable.unpaged())
                .stream().filter(r -> r.getTransactionId().equals(txn)).findFirst().orElseThrow().getId();
        String auditorToken = mintToken("AUDITOR");

        ResponseEntity<Map> viewResponse = restTemplate.exchange("/api/v1/ledger/reconciliation-records/" + recordId,
                HttpMethod.GET, bearer(auditorToken), Map.class);
        assertThat(viewResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<Map> investigateResponse = restTemplate.exchange(
                "/api/v1/ledger/reconciliation-records/" + recordId + "/start-investigation", HttpMethod.PATCH,
                bearer(auditorToken), Map.class);
        assertThat(investigateResponse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void fullInvestigateAndResolveWorkflow_succeedsForOperationsStaff() {
        UUID txn = postAMismatchingTransaction();
        reconciliationService.run();
        UUID recordId = reconciliationService.queue(null, org.springframework.data.domain.Pageable.unpaged())
                .stream().filter(r -> r.getTransactionId().equals(txn)).findFirst().orElseThrow().getId();
        String opsToken = mintToken("OPERATIONS");

        ResponseEntity<Map> investigateResponse = restTemplate.exchange(
                "/api/v1/ledger/reconciliation-records/" + recordId + "/start-investigation", HttpMethod.PATCH,
                bearer(opsToken), Map.class);
        assertThat(investigateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(investigateResponse.getBody().get("status")).isEqualTo("INVESTIGATION");

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(opsToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<ResolutionRequest> resolveEntity = new HttpEntity<>(
                new ResolutionRequest("external feed was delayed, confirmed manually"), headers);
        ResponseEntity<Map> resolveResponse = restTemplate.exchange(
                "/api/v1/ledger/reconciliation-records/" + recordId + "/resolve", HttpMethod.PATCH, resolveEntity,
                Map.class);

        assertThat(resolveResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resolveResponse.getBody().get("status")).isEqualTo("RESOLVED");
        assertThat(resolveResponse.getBody().get("resolutionNotes")).isEqualTo("external feed was delayed, confirmed manually");
    }

    @Test
    void unknownRecordId_returns404() {
        String token = mintToken("OPERATIONS");
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/ledger/reconciliation-records/" + UUID.randomUUID(), HttpMethod.GET, bearer(token),
                Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
