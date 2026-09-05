package com.meridianbank.kyc.client;

import com.meridianbank.kyc.exception.AuthServiceUnavailableException;
import com.meridianbank.kyc.exception.EmailAlreadyRegisteredException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.UUID;

/**
 * Synchronous call to auth-service to provision a customer's login identity at registration
 * time. See docs/adr/0014-circuit-breaker.md — the request has a connect/read timeout (set on
 * the underlying HTTP client in RestClientConfig) and is wrapped in a circuit breaker so a
 * struggling auth-service fails fast rather than exhausting this service's request threads.
 * Never retried automatically: registration is not safely repeatable the way an idempotency-keyed
 * payment is (see docs/adr/0006-idempotency.md) — a failure here is surfaced to the caller.
 */
@Component
public class AuthServiceClient {

    private final RestClient restClient;

    public AuthServiceClient(RestClient authServiceRestClient) {
        this.restClient = authServiceRestClient;
    }

    @CircuitBreaker(name = "authService", fallbackMethod = "registerIdentityFallback")
    public CreatedIdentity registerIdentity(String email, String password) {
        try {
            return restClient.post()
                    .uri("/api/v1/auth/register")
                    .body(new RegisterRequest(email, password))
                    .retrieve()
                    .body(CreatedIdentity.class);
        } catch (HttpClientErrorException.Conflict e) {
            throw new EmailAlreadyRegisteredException();
        } catch (ResourceAccessException e) {
            throw new AuthServiceUnavailableException(e);
        }
    }

    @SuppressWarnings("unused")
    private CreatedIdentity registerIdentityFallback(String email, String password, Throwable t) {
        if (t instanceof EmailAlreadyRegisteredException || t instanceof AuthServiceUnavailableException) {
            throw (RuntimeException) t;
        }
        throw new AuthServiceUnavailableException(t);
    }

    public record RegisterRequest(String email, String password) {
    }

    public record CreatedIdentity(UUID id, String email, String role, String status, boolean mfaEnabled,
                                   Instant lastLoginAt, Instant createdAt) {
    }
}
