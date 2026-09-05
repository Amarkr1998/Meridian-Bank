package com.meridianbank.gateway.web;

import com.meridianbank.gateway.config.GatewayServiceUrls;
import com.meridianbank.gateway.security.JwtVerifier;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Aggregated health across every service — the one Phase 15 ops screen ({@code /ops/system-health})
 * with nothing to proxy to, since no single downstream service can answer "is everything up".
 * Handled entirely locally (see {@code RouteRegistry}'s {@code null}-baseUrl entry and
 * {@code ProxyFilter}'s handling of it) rather than proxied.
 *
 * <p>Role-gated here, in the gateway itself, because there is no downstream service to defer that
 * check to — the one exception to this gateway never trusting a token's claims (see
 * {@link JwtVerifier#decodeRole}'s Javadoc). EdgeAuthFilter has already confirmed the token is
 * present and validly signed before this controller ever runs.
 */
@RestController
@RequestMapping("/api/v1/ops/system-health")
public class SystemHealthController {

    private static final Set<String> STAFF_ROLES =
            Set.of("OPERATIONS", "COMPLIANCE_OFFICER", "RISK_ANALYST", "AUDITOR", "ADMIN");

    private final RestClient restClient;
    private final JwtVerifier jwtVerifier;
    private final Map<String, String> serviceUrls;

    public SystemHealthController(RestClient upstreamRestClient, JwtVerifier jwtVerifier, GatewayServiceUrls urls) {
        this.restClient = upstreamRestClient;
        this.jwtVerifier = jwtVerifier;
        this.serviceUrls = new LinkedHashMap<>();
        serviceUrls.put("auth-service", urls.authService());
        serviceUrls.put("customer-kyc-service", urls.customerKycService());
        serviceUrls.put("account-service", urls.accountService());
        serviceUrls.put("payment-service", urls.paymentService());
        serviceUrls.put("ledger-service", urls.ledgerService());
        serviceUrls.put("fraud-risk-service", urls.fraudRiskService());
        serviceUrls.put("audit-service", urls.auditService());
        serviceUrls.put("notification-service", urls.notificationService());
    }

    public record ServiceHealth(String service, String status) {
    }

    @GetMapping
    public ResponseEntity<?> systemHealth(@RequestHeader("Authorization") String authorizationHeader,
                                           HttpServletResponse response) {
        ErrorResponseWriter.applyBaselineSecurityHeaders(response);
        String role = jwtVerifier.decodeRole(authorizationHeader.substring(7));
        if (role == null || !STAFF_ROLES.contains(role)) {
            return ResponseEntity.status(403).body(new ErrorResponse("FORBIDDEN", "Staff role required", null));
        }

        java.util.List<ServiceHealth> results = new java.util.ArrayList<>();
        results.add(new ServiceHealth("api-gateway", "UP"));
        serviceUrls.forEach((service, baseUrl) -> results.add(new ServiceHealth(service, checkHealth(baseUrl))));
        return ResponseEntity.ok(results);
    }

    private String checkHealth(String baseUrl) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> body = restClient.get().uri(baseUrl + "/actuator/health")
                    .retrieve().body(Map.class);
            Object status = body != null ? body.get("status") : null;
            return status != null ? status.toString() : "UNKNOWN";
        } catch (Exception e) {
            return "DOWN";
        }
    }
}
