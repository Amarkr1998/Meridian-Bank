package com.meridianbank.account.client;

import com.meridianbank.account.exception.CustomerKycServiceUnavailableException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.util.Arrays;
import java.util.UUID;

/**
 * Checks whether a customer has a KYC_VERIFIED record before an account opening request is
 * accepted. Calls GET /api/v1/customers/{id}/kyc on customer-kyc-service, forwarding the
 * caller's own bearer token — that endpoint's authorization rule
 * ({@code #id == authentication.principal or hasAnyRole(...)}) passes because account opening is
 * always a self-service action, so the customer's own token is what's being forwarded on their
 * behalf. See docs/adr/0014-circuit-breaker.md — never retried, wrapped in a circuit breaker.
 */
@Component
public class CustomerKycServiceClient {

    private final RestClient restClient;

    public CustomerKycServiceClient(RestClient customerKycServiceRestClient) {
        this.restClient = customerKycServiceRestClient;
    }

    @CircuitBreaker(name = "customerKycService", fallbackMethod = "hasVerifiedKycFallback")
    public boolean hasVerifiedKyc(UUID customerId, String bearerToken) {
        try {
            KycRecordSummary[] records = restClient.get()
                    .uri("/api/v1/customers/{id}/kyc", customerId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken)
                    .retrieve()
                    .body(KycRecordSummary[].class);
            return records != null && Arrays.stream(records).anyMatch(r -> "KYC_VERIFIED".equals(r.status()));
        } catch (HttpClientErrorException.Forbidden | HttpClientErrorException.Unauthorized e) {
            return false;
        } catch (ResourceAccessException e) {
            throw new CustomerKycServiceUnavailableException(e);
        }
    }

    @SuppressWarnings("unused")
    private boolean hasVerifiedKycFallback(UUID customerId, String bearerToken, Throwable t) {
        if (t instanceof CustomerKycServiceUnavailableException e) {
            throw e;
        }
        throw new CustomerKycServiceUnavailableException(t);
    }

    /** Only the field this client needs — Spring's default Jackson config ignores the rest. */
    record KycRecordSummary(String status) {
    }
}
