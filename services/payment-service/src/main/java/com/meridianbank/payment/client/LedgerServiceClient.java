package com.meridianbank.payment.client;

import com.meridianbank.payment.exception.UpstreamServiceUnavailableException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Posts the real double-entry pair to ledger-service — see
 * docs/adr/0007-double-entry-ledger.md. This is the call that finally moves real money: every
 * check in PaymentService before this point exists to decide whether it's safe to make it. See
 * docs/adr/0014-circuit-breaker.md — wrapped in a circuit breaker, never retried.
 */
@Component
public class LedgerServiceClient {

    private final RestClient restClient;

    public LedgerServiceClient(RestClient ledgerServiceRestClient) {
        this.restClient = ledgerServiceRestClient;
    }

    @CircuitBreaker(name = "ledgerService", fallbackMethod = "postFallback")
    public PostingResult post(UUID transactionId, UUID debitAccountId, UUID creditAccountId, BigDecimal amount,
                               String currency, String reference, String bearerToken) {
        try {
            return restClient.post()
                    .uri("/api/v1/ledger/postings")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken)
                    .body(new PostingRequest(transactionId, debitAccountId, creditAccountId, amount, currency, reference))
                    .retrieve()
                    .body(PostingResult.class);
        } catch (HttpClientErrorException.Conflict e) {
            throw new InsufficientBalanceException();
        } catch (ResourceAccessException e) {
            throw new UpstreamServiceUnavailableException("ledger-service", e);
        }
    }

    @SuppressWarnings("unused")
    private PostingResult postFallback(UUID transactionId, UUID debitAccountId, UUID creditAccountId,
                                        BigDecimal amount, String currency, String reference, String bearerToken,
                                        Throwable t) {
        if (t instanceof InsufficientBalanceException e) {
            throw e;
        }
        if (t instanceof UpstreamServiceUnavailableException e) {
            throw e;
        }
        throw new UpstreamServiceUnavailableException("ledger-service", t);
    }

    public record PostingRequest(UUID transactionId, UUID debitAccountId, UUID creditAccountId, BigDecimal amount,
                                  String currency, String reference) {
    }

    public record PostingResult(UUID transactionId, UUID debitEntryId, UUID creditEntryId,
                                 BigDecimal debitAccountAvailableBalance, BigDecimal creditAccountAvailableBalance) {
    }
}
