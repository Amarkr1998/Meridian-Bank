package com.meridianbank.payment.client;

import com.meridianbank.payment.exception.UpstreamServiceUnavailableException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Calls fraud-risk-service's risk assessment during RISK_CHECK — see
 * docs/architecture/fraud-flow.md. Same fail-hard posture as every other synchronous validation
 * dependency in this pipeline (account-service, customer-kyc-service, ledger-service): if
 * fraud-risk-service can't be reached, the whole request fails rather than silently allowing (or
 * silently blocking) the payment — see docs/adr/0014-circuit-breaker.md, wrapped in a circuit
 * breaker, never retried.
 */
@Component
public class FraudRiskServiceClient {

    private final RestClient restClient;

    public FraudRiskServiceClient(RestClient fraudRiskServiceRestClient) {
        this.restClient = fraudRiskServiceRestClient;
    }

    @CircuitBreaker(name = "fraudRiskService", fallbackMethod = "assessFallback")
    public RiskAssessmentResult assess(UUID transactionId, UUID customerId, UUID sourceAccountId,
                                        UUID destinationAccountId, BigDecimal amount, String currency,
                                        String bearerToken) {
        try {
            return restClient.post()
                    .uri("/api/v1/risk-assessments")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken)
                    .body(new RiskAssessmentRequest(transactionId, customerId, sourceAccountId, destinationAccountId,
                            amount, currency))
                    .retrieve()
                    .body(RiskAssessmentResult.class);
        } catch (ResourceAccessException e) {
            throw new UpstreamServiceUnavailableException("fraud-risk-service", e);
        }
    }

    @SuppressWarnings("unused")
    private RiskAssessmentResult assessFallback(UUID transactionId, UUID customerId, UUID sourceAccountId,
                                                  UUID destinationAccountId, BigDecimal amount, String currency,
                                                  String bearerToken, Throwable t) {
        if (t instanceof UpstreamServiceUnavailableException e) {
            throw e;
        }
        throw new UpstreamServiceUnavailableException("fraud-risk-service", t);
    }

    public record RiskAssessmentRequest(UUID transactionId, UUID customerId, UUID sourceAccountId,
                                         UUID destinationAccountId, BigDecimal amount, String currency) {
    }

    public record RiskAssessmentResult(UUID transactionId, int score, String decision, String ruleHits) {
    }
}
