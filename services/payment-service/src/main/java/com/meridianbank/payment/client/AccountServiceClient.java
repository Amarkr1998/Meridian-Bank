package com.meridianbank.payment.client;

import com.meridianbank.payment.exception.UpstreamServiceUnavailableException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/**
 * Validates the source account and beneficiary for a payment by calling account-service,
 * forwarding the customer's own bearer token. A 404/403 from either call means "not a usable
 * source account / beneficiary for this caller" — payment-service doesn't need to distinguish
 * why (doesn't exist vs. not owned); that ambiguity is itself the correct security posture. See
 * docs/adr/0014-circuit-breaker.md — wrapped in a circuit breaker, never retried.
 */
@Component
public class AccountServiceClient {

    private final RestClient restClient;

    public AccountServiceClient(RestClient accountServiceRestClient) {
        this.restClient = accountServiceRestClient;
    }

    @CircuitBreaker(name = "accountService", fallbackMethod = "getAccountFallback")
    public Optional<AccountSummary> getAccount(UUID accountId, String bearerToken) {
        try {
            AccountSummary summary = restClient.get()
                    .uri("/api/v1/accounts/{id}", accountId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken)
                    .retrieve()
                    .body(AccountSummary.class);
            return Optional.ofNullable(summary);
        } catch (HttpClientErrorException.NotFound | HttpClientErrorException.Forbidden e) {
            return Optional.empty();
        } catch (ResourceAccessException e) {
            throw new UpstreamServiceUnavailableException("account-service", e);
        }
    }

    @SuppressWarnings("unused")
    private Optional<AccountSummary> getAccountFallback(UUID accountId, String bearerToken, Throwable t) {
        if (t instanceof UpstreamServiceUnavailableException e) {
            throw e;
        }
        throw new UpstreamServiceUnavailableException("account-service", t);
    }

    @CircuitBreaker(name = "accountService", fallbackMethod = "getBeneficiaryFallback")
    public Optional<BeneficiarySummary> getBeneficiary(UUID beneficiaryId, String bearerToken) {
        try {
            BeneficiarySummary summary = restClient.get()
                    .uri("/api/v1/beneficiaries/{id}", beneficiaryId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken)
                    .retrieve()
                    .body(BeneficiarySummary.class);
            return Optional.ofNullable(summary);
        } catch (HttpClientErrorException.NotFound | HttpClientErrorException.Forbidden e) {
            return Optional.empty();
        } catch (ResourceAccessException e) {
            throw new UpstreamServiceUnavailableException("account-service", e);
        }
    }

    @SuppressWarnings("unused")
    private Optional<BeneficiarySummary> getBeneficiaryFallback(UUID beneficiaryId, String bearerToken, Throwable t) {
        if (t instanceof UpstreamServiceUnavailableException e) {
            throw e;
        }
        throw new UpstreamServiceUnavailableException("account-service", t);
    }

    /** Mirrors the subset of account-service's AccountResponse this service needs. */
    public record AccountSummary(UUID id, String status, String currency, BigDecimal perTransactionLimit,
                                  BigDecimal dailyLimit) {
    }

    /** Mirrors the subset of account-service's BeneficiaryResponse this service needs — never the
     *  raw account number, only the resolved destination account id/status. */
    public record BeneficiarySummary(UUID id, String status, UUID destinationAccountId,
                                      String destinationAccountStatus) {
    }
}
