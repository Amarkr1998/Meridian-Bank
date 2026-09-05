package com.meridianbank.account.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/**
 * Looks up a real balance from ledger-service to enrich {@code GET /api/v1/accounts/{id}} — see
 * docs/adr/0007-double-entry-ledger.md. Unlike the KYC-verification gate (which must fail
 * *closed* — no verified KYC, no account), balance enrichment fails *open*: a real exception
 * still propagates from {@link #getBalance} so the circuit breaker tracks it correctly, but the
 * fallback always degrades to {@link Optional#empty()} rather than breaking basic account
 * viewing. The caller's own bearer token is forwarded, since account-service's endpoint has
 * already established ownership by the time this is called.
 */
@Component
public class LedgerServiceClient {

    private static final Logger log = LoggerFactory.getLogger(LedgerServiceClient.class);

    private final RestClient restClient;

    public LedgerServiceClient(RestClient ledgerServiceRestClient) {
        this.restClient = ledgerServiceRestClient;
    }

    @CircuitBreaker(name = "ledgerService", fallbackMethod = "getBalanceFallback")
    public Optional<BalanceSummary> getBalance(UUID accountId, String bearerToken) {
        try {
            BalanceSummary balance = restClient.get()
                    .uri("/api/v1/ledger/accounts/{id}/balance", accountId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken)
                    .retrieve()
                    .body(BalanceSummary.class);
            return Optional.ofNullable(balance);
        } catch (HttpClientErrorException | ResourceAccessException e) {
            throw new LedgerServiceUnavailableException(e);
        }
    }

    @SuppressWarnings("unused")
    private Optional<BalanceSummary> getBalanceFallback(UUID accountId, String bearerToken, Throwable t) {
        log.warn("Could not fetch balance for account {} from ledger-service: {}", accountId, t.getMessage());
        return Optional.empty();
    }

    private static class LedgerServiceUnavailableException extends RuntimeException {
        LedgerServiceUnavailableException(Throwable cause) {
            super("ledger-service unavailable", cause);
        }
    }

    public record BalanceSummary(BigDecimal availableBalance, BigDecimal ledgerBalance, String currency) {
    }
}
