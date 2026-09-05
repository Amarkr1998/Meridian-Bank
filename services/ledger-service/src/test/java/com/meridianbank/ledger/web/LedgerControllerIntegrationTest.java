package com.meridianbank.ledger.web;

import com.meridianbank.ledger.domain.Balance;
import com.meridianbank.ledger.repository.BalanceRepository;
import com.meridianbank.ledger.web.dto.BalanceResponse;
import com.meridianbank.ledger.web.dto.ErrorResponse;
import com.meridianbank.ledger.web.dto.PostingRequest;
import com.meridianbank.ledger.web.dto.PostingResponse;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Full-stack coverage of the HTTP contract against real Postgres. Real concurrency correctness
 *  is proven separately in LedgerPostingConcurrencyTest. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class LedgerControllerIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final String JWT_SECRET = "meridian-bank-local-demo-jwt-signing-secret-change-me-32bytes-min";
    private static final SecretKey SIGNING_KEY = Keys.hmacShaKeyFor(JWT_SECRET.getBytes(StandardCharsets.UTF_8));

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private BalanceRepository balanceRepository;

    /** Test-fixture-only: a real double-entry ledger can't create balance from nothing — every
     *  credit needs a matching debit (see LedgerPostingService). Seeding a starting balance for a
     *  test therefore writes directly to the repository rather than posting from an empty
     *  "funding" account. */
    private void seedBalance(UUID accountId, String amount) {
        Balance balance = new Balance(accountId, "USD");
        balance.credit(new BigDecimal(amount));
        balanceRepository.save(balance);
    }

    private String mintToken() {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer("test")
                .subject(UUID.randomUUID().toString())
                .claim("email", "caller@example.com")
                .claim("role", "CUSTOMER")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(900)))
                .signWith(SIGNING_KEY)
                .compact();
    }

    private <T> HttpEntity<T> bearer(T body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(mintToken());
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }

    @Test
    void postingWithoutAuthentication_isRejected() {
        PostingRequest request = new PostingRequest(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                BigDecimal.TEN, "USD", null);
        ResponseEntity<ErrorResponse> response = restTemplate.postForEntity("/api/v1/ledger/postings", request, ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void insufficientBalance_returns409() {
        UUID debitAccountId = UUID.randomUUID();
        UUID creditAccountId = UUID.randomUUID();
        PostingRequest request = new PostingRequest(UUID.randomUUID(), debitAccountId, creditAccountId,
                new BigDecimal("50.00"), "USD", "test");

        ResponseEntity<ErrorResponse> response = restTemplate.exchange("/api/v1/ledger/postings", HttpMethod.POST,
                bearer(request), ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().code()).isEqualTo("INSUFFICIENT_BALANCE");
    }

    @Test
    void fullPostingFlow_updatesBothBalancesAndListsEntries() {
        UUID debitAccountId = UUID.randomUUID();
        UUID creditAccountId = UUID.randomUUID();
        seedBalance(debitAccountId, "200.00");

        ResponseEntity<PostingResponse> posted = restTemplate.exchange("/api/v1/ledger/postings", HttpMethod.POST,
                bearer(new PostingRequest(UUID.randomUUID(), debitAccountId, creditAccountId,
                        new BigDecimal("75.00"), "USD", "rent")),
                PostingResponse.class);
        assertThat(posted.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(posted.getBody().debitAccountAvailableBalance()).isEqualByComparingTo("125.00");
        assertThat(posted.getBody().creditAccountAvailableBalance()).isEqualByComparingTo("75.00");

        ResponseEntity<BalanceResponse> balance = restTemplate.exchange("/api/v1/ledger/accounts/{id}/balance",
                HttpMethod.GET, bearer(null), BalanceResponse.class, debitAccountId);
        assertThat(balance.getBody().availableBalance()).isEqualByComparingTo("125.00");

        ResponseEntity<String> entries = restTemplate.exchange("/api/v1/ledger/accounts/{id}/entries",
                HttpMethod.GET, bearer(null), String.class, debitAccountId);
        assertThat(entries.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(entries.getBody()).contains("DEBIT");
    }

    @Test
    void balanceForNeverSeenAccount_isZeroNotAnError() {
        UUID unseenAccountId = UUID.randomUUID();
        ResponseEntity<BalanceResponse> response = restTemplate.exchange("/api/v1/ledger/accounts/{id}/balance",
                HttpMethod.GET, bearer(null), BalanceResponse.class, unseenAccountId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().availableBalance()).isEqualByComparingTo("0.00");
    }
}
