package com.meridianbank.payment.client;

import com.meridianbank.payment.exception.UpstreamServiceUnavailableException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.util.Arrays;
import java.util.UUID;

/**
 * Checks whether the paying customer has a KYC_VERIFIED record, forwarding the caller's own
 * bearer token — same pattern as account-service's identically-named client (see its README for
 * why token-forwarding satisfies the downstream resource-ownership check here).
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
            throw new UpstreamServiceUnavailableException("customer-kyc-service", e);
        }
    }

    @SuppressWarnings("unused")
    private boolean hasVerifiedKycFallback(UUID customerId, String bearerToken, Throwable t) {
        if (t instanceof UpstreamServiceUnavailableException e) {
            throw e;
        }
        throw new UpstreamServiceUnavailableException("customer-kyc-service", t);
    }

    record KycRecordSummary(String status) {
    }
}
