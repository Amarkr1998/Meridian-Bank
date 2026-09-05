package com.meridianbank.fraud.web;

import com.meridianbank.fraud.approval.ApprovalDecisionRequest;
import com.meridianbank.fraud.approval.ApprovalRequestResponse;
import com.meridianbank.fraud.web.dto.*;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.crypto.SecretKey;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full-stack coverage against real Postgres (Testcontainers) — no mocking of the rule engine or
 * its data source, since proving the velocity/frequency rules actually accumulate correctly
 * against a real database (not a mocked repository) is the point of this test. Covers the
 * complete risk-assessment → fraud-alert-created → review → clear flow, RBAC for both fraud and
 * AML alerts (a narrower surface for AML — see docs/architecture/aml-flow.md), and configuring a
 * rule via PATCH /fraud-rules/{id}. See docs/architecture/fraud-flow.md and aml-flow.md.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class FraudRiskControllerIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final String JWT_SECRET = "meridian-bank-local-demo-jwt-signing-secret-change-me-32bytes-min";
    private static final SecretKey SIGNING_KEY = Keys.hmacShaKeyFor(JWT_SECRET.getBytes(StandardCharsets.UTF_8));

    @Autowired
    private TestRestTemplate restTemplate;

    private String mintToken(UUID userId, String email, String role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer("test")
                .subject(userId.toString())
                .claim("email", email)
                .claim("role", role)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(900)))
                .signWith(SIGNING_KEY)
                .compact();
    }

    private <T> HttpEntity<T> bearer(String token, T body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }

    private HttpEntity<Void> bearer(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return new HttpEntity<>(headers);
    }

    private RiskAssessmentRequest paymentRequest(UUID customerId, UUID source, UUID destination, BigDecimal amount) {
        return new RiskAssessmentRequest(UUID.randomUUID(), customerId, source, destination, amount, "USD");
    }

    /** Mirrors just enough of Spring Data's Page JSON shape ({"content": [...], ...}) to read list
     *  results back — FraudAlertController/AmlAlertController return Page<T>, which serializes as
     *  a JSON object, not a bare array. */
    private record PageEnvelope<T>(List<T> content) {
    }

    private <T> ResponseEntity<PageEnvelope<T>> pageResponse(String url, String token,
                                                               ParameterizedTypeReference<PageEnvelope<T>> type) {
        return restTemplate.exchange(url, HttpMethod.GET, bearer(token), type);
    }

    @Test
    void highAmountAssessment_createsFraudAlertVisibleToRiskAnalyst_andLifecycleCompletes() {
        String paymentServiceToken = mintToken(UUID.randomUUID(), "payment-service-caller@example.com", "CUSTOMER");
        UUID customerId = UUID.randomUUID();

        ResponseEntity<RiskAssessmentResponse> assessed = restTemplate.exchange("/api/v1/risk-assessments",
                HttpMethod.POST, bearer(paymentServiceToken, paymentRequest(customerId, UUID.randomUUID(),
                        UUID.randomUUID(), new BigDecimal("9000.00"))), RiskAssessmentResponse.class);

        assertThat(assessed.getStatusCode()).isEqualTo(HttpStatus.OK);
        // $9000 to a brand-new destination trips BOTH default rules: HIGH_AMOUNT (40, threshold
        // $5000) and NEW_BENEFICIARY_HIGH_AMOUNT (25, threshold $1000, first transfer to this
        // destination) -> 65, which is still within the REVIEW band (31-70).
        assertThat(assessed.getBody().decision()).isEqualTo("REVIEW");
        assertThat(assessed.getBody().score()).isEqualTo(65);
        assertThat(assessed.getBody().ruleHits()).contains("HIGH_AMOUNT", "NEW_BENEFICIARY_HIGH_AMOUNT");

        String riskAnalystToken = mintToken(UUID.randomUUID(), "analyst@meridianbank.local", "RISK_ANALYST");
        ResponseEntity<PageEnvelope<FraudAlertResponse>> queueResponse = pageResponse(
                "/api/v1/fraud-alerts?status=OPEN", riskAnalystToken,
                new ParameterizedTypeReference<PageEnvelope<FraudAlertResponse>>() {
                });
        assertThat(queueResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<FraudAlertResponse> queue = queueResponse.getBody().content();
        assertThat(queue).isNotEmpty();
        FraudAlertResponse alert = queue.stream()
                .filter(a -> a.transactionId().equals(assessed.getBody().transactionId()))
                .findFirst().orElseThrow();
        assertThat(alert.status()).isEqualTo("OPEN");
        assertThat(alert.ruleHits()).contains("HIGH_AMOUNT");

        ResponseEntity<FraudAlertResponse> started = restTemplate.exchange(
                "/api/v1/fraud-alerts/{id}/start-review", HttpMethod.PATCH, bearer(riskAnalystToken),
                FraudAlertResponse.class, alert.id());
        assertThat(started.getBody().status()).isEqualTo("UNDER_REVIEW");

        // As of Phase 10, resolving the alert is maker-checker gated (docs/adr/0011-maker-checker.md,
        // "high-risk fraud decisions") — the RISK_ANALYST's "clear" only creates a request.
        ResponseEntity<ApprovalRequestResponse> clearRequested = restTemplate.exchange(
                "/api/v1/fraud-alerts/{id}/clear", HttpMethod.PATCH,
                bearer(riskAnalystToken, new ResolutionRequest("verified with customer — legitimate")),
                ApprovalRequestResponse.class, alert.id());
        assertThat(clearRequested.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(clearRequested.getBody().status()).isEqualTo("PENDING_APPROVAL");

        ResponseEntity<FraudAlertResponse> stillUnderReview = restTemplate.exchange("/api/v1/fraud-alerts/{id}",
                HttpMethod.GET, bearer(riskAnalystToken), FraudAlertResponse.class, alert.id());
        assertThat(stillUnderReview.getBody().status()).isEqualTo("UNDER_REVIEW");

        // A different staff member (the checker) approves — only now does the alert resolve.
        String checkerToken = mintToken(UUID.randomUUID(), "checker@meridianbank.local", "COMPLIANCE_OFFICER");
        ResponseEntity<ApprovalRequestResponse> decided = restTemplate.exchange("/api/v1/approvals/{id}/approve",
                HttpMethod.PATCH, bearer(checkerToken, new ApprovalDecisionRequest("confirmed legitimate")),
                ApprovalRequestResponse.class, clearRequested.getBody().id());
        assertThat(decided.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(decided.getBody().status()).isEqualTo("APPROVED");

        ResponseEntity<FraudAlertResponse> cleared = restTemplate.exchange("/api/v1/fraud-alerts/{id}",
                HttpMethod.GET, bearer(riskAnalystToken), FraudAlertResponse.class, alert.id());
        assertThat(cleared.getBody().status()).isEqualTo("CLEARED");
        assertThat(cleared.getBody().resolutionNotes()).isEqualTo("verified with customer — legitimate");
    }

    @Test
    void alertResolution_riskAnalystCannotApproveTheirOwnRequest() {
        String paymentServiceToken = mintToken(UUID.randomUUID(), "payment-service-caller2@example.com", "CUSTOMER");
        ResponseEntity<RiskAssessmentResponse> assessed = restTemplate.exchange("/api/v1/risk-assessments",
                HttpMethod.POST, bearer(paymentServiceToken, paymentRequest(UUID.randomUUID(), UUID.randomUUID(),
                        UUID.randomUUID(), new BigDecimal("9000.00"))), RiskAssessmentResponse.class);

        String riskAnalystToken = mintToken(UUID.randomUUID(), "analyst3@meridianbank.local", "RISK_ANALYST");
        List<FraudAlertResponse> queue = pageResponse("/api/v1/fraud-alerts?status=OPEN", riskAnalystToken,
                new ParameterizedTypeReference<PageEnvelope<FraudAlertResponse>>() {
                }).getBody().content();
        FraudAlertResponse alert = queue.stream()
                .filter(a -> a.transactionId().equals(assessed.getBody().transactionId()))
                .findFirst().orElseThrow();
        restTemplate.exchange("/api/v1/fraud-alerts/{id}/start-review", HttpMethod.PATCH, bearer(riskAnalystToken),
                FraudAlertResponse.class, alert.id());
        ApprovalRequestResponse request = restTemplate.exchange("/api/v1/fraud-alerts/{id}/clear", HttpMethod.PATCH,
                bearer(riskAnalystToken, new ResolutionRequest("looks fine")),
                ApprovalRequestResponse.class, alert.id()).getBody();

        ResponseEntity<ErrorResponse> selfApproval = restTemplate.exchange("/api/v1/approvals/{id}/approve",
                HttpMethod.PATCH, bearer(riskAnalystToken), ErrorResponse.class, request.id());
        assertThat(selfApproval.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(selfApproval.getBody().code()).isEqualTo("SELF_APPROVAL_NOT_ALLOWED");
    }

    @Test
    void rapidSequentialTransfers_realVelocityAccumulatesAcrossCallsAndEventuallyBlocks() {
        // Proves HIGH_VELOCITY/RAPID_SEQUENTIAL_TRANSFERS against a REAL database — each call's
        // score depends on the PRIOR calls' rows genuinely being there, not a mocked count.
        String token = mintToken(UUID.randomUUID(), "payment-service-caller@example.com", "CUSTOMER");
        UUID customerId = UUID.randomUUID();
        UUID source = UUID.randomUUID();

        List<String> decisions = new java.util.ArrayList<>();
        for (int i = 0; i < 4; i++) {
            ResponseEntity<RiskAssessmentResponse> response = restTemplate.exchange("/api/v1/risk-assessments",
                    HttpMethod.POST, bearer(token, paymentRequest(customerId, source, UUID.randomUUID(),
                            new BigDecimal("10.00"))), RiskAssessmentResponse.class);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            decisions.add(response.getBody().decision());
        }

        // Default RAPID_SEQUENTIAL_TRANSFERS: weight 45, threshold 3 within 60s. By the 3rd
        // assessment there are 2 prior rows (>= threshold 3? no: count query is BEFORE this row
        // is saved, so the 3rd call sees 2 priors — not yet 3) — the 4th call sees 3 priors,
        // meeting the threshold and adding 45 points, moving it out of ALLOW.
        assertThat(decisions.get(0)).isEqualTo("ALLOW");
        assertThat(decisions.get(3)).isNotEqualTo("ALLOW");
    }

    @Test
    void amlHighValueAssessment_createsAmlAlert_riskAnalystCanViewButNotClear() {
        String paymentToken = mintToken(UUID.randomUUID(), "payment-service-caller@example.com", "CUSTOMER");
        UUID customerId = UUID.randomUUID();

        ResponseEntity<RiskAssessmentResponse> assessed = restTemplate.exchange("/api/v1/risk-assessments",
                HttpMethod.POST, bearer(paymentToken, paymentRequest(customerId, UUID.randomUUID(),
                        UUID.randomUUID(), new BigDecimal("10000.00"))), RiskAssessmentResponse.class);
        assertThat(assessed.getStatusCode()).isEqualTo(HttpStatus.OK);

        String riskAnalystToken = mintToken(UUID.randomUUID(), "analyst2@meridianbank.local", "RISK_ANALYST");
        ResponseEntity<PageEnvelope<AmlAlertResponse>> queueResponse = pageResponse(
                "/api/v1/aml-alerts?status=OPEN", riskAnalystToken,
                new ParameterizedTypeReference<PageEnvelope<AmlAlertResponse>>() {
                });
        assertThat(queueResponse.getStatusCode()).isEqualTo(HttpStatus.OK); // RISK_ANALYST CAN view
        AmlAlertResponse amlAlert = queueResponse.getBody().content().stream()
                .filter(a -> a.transactionId().equals(assessed.getBody().transactionId()))
                .findFirst().orElseThrow();
        assertThat(amlAlert.signalCode()).isEqualTo("HIGH_VALUE");

        ResponseEntity<ErrorResponse> forbidden = restTemplate.exchange("/api/v1/aml-alerts/{id}/start-review",
                HttpMethod.PATCH, bearer(riskAnalystToken), ErrorResponse.class, amlAlert.id());
        assertThat(forbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN); // RISK_ANALYST cannot transition

        String complianceToken = mintToken(UUID.randomUUID(), "compliance@meridianbank.local", "COMPLIANCE_OFFICER");
        ResponseEntity<AmlAlertResponse> started = restTemplate.exchange("/api/v1/aml-alerts/{id}/start-review",
                HttpMethod.PATCH, bearer(complianceToken), AmlAlertResponse.class, amlAlert.id());
        assertThat(started.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(started.getBody().status()).isEqualTo("UNDER_REVIEW");
    }

    @Test
    void customerCannotAccessAlertQueuesOrRiskSummary_butCanStillReachRiskAssessments() {
        String customerToken = mintToken(UUID.randomUUID(), "customer@example.com", "CUSTOMER");

        ResponseEntity<ErrorResponse> alerts = restTemplate.exchange("/api/v1/fraud-alerts", HttpMethod.GET,
                bearer(customerToken), ErrorResponse.class);
        assertThat(alerts.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<ErrorResponse> summary = restTemplate.exchange("/api/v1/customers/{id}/risk-summary",
                HttpMethod.GET, bearer(customerToken), ErrorResponse.class, UUID.randomUUID());
        assertThat(summary.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // POST /risk-assessments deliberately has no role check — see its Trust Boundary note.
        // Any authenticated caller (in practice, only payment-service) can reach it; a CUSTOMER
        // token exercising this path just proves it isn't accidentally staff-gated.
        ResponseEntity<RiskAssessmentResponse> assessed = restTemplate.exchange("/api/v1/risk-assessments",
                HttpMethod.POST, bearer(customerToken, paymentRequest(UUID.randomUUID(), UUID.randomUUID(),
                        UUID.randomUUID(), new BigDecimal("10.00"))), RiskAssessmentResponse.class);
        assertThat(assessed.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void updatingAFraudRule_changesSubsequentAssessments() {
        // This test mutates GLOBAL rule configuration, shared with every other test method in
        // this class (one static Testcontainers Postgres for the whole class, same as every
        // other *IntegrationTest in this project) — the disable/restore must be symmetric and
        // guaranteed via try/finally, or a later test method could run against a HIGH_AMOUNT rule
        // this test forgot to turn back on.
        String adminToken = mintToken(UUID.randomUUID(), "admin@meridianbank.local", "ADMIN");
        UUID customerId = UUID.randomUUID();
        UUID source = UUID.randomUUID();
        UUID destination = UUID.randomUUID();
        String paymentToken = mintToken(UUID.randomUUID(), "payment-service-caller@example.com", "CUSTOMER");

        // Seed this destination as "known" for this customer first, so the large assessment below
        // doesn't also trip NEW_BENEFICIARY_HIGH_AMOUNT — this test isolates HIGH_AMOUNT alone.
        restTemplate.exchange("/api/v1/risk-assessments", HttpMethod.POST,
                bearer(paymentToken, paymentRequest(customerId, source, destination, new BigDecimal("1.00"))),
                RiskAssessmentResponse.class);

        ResponseEntity<FraudRuleResponse[]> rules = restTemplate.exchange("/api/v1/fraud-rules", HttpMethod.GET,
                bearer(adminToken), FraudRuleResponse[].class);
        FraudRuleResponse highAmountRule = List.of(rules.getBody()).stream()
                .filter(r -> r.ruleCode().equals("HIGH_AMOUNT")).findFirst().orElseThrow();

        UpdateFraudRuleRequest disableIt = new UpdateFraudRuleRequest(highAmountRule.weight(),
                highAmountRule.thresholdNumeric(), highAmountRule.thresholdWindowSeconds(),
                highAmountRule.thresholdCount(), false);
        UpdateFraudRuleRequest restoreIt = new UpdateFraudRuleRequest(highAmountRule.weight(),
                highAmountRule.thresholdNumeric(), highAmountRule.thresholdWindowSeconds(),
                highAmountRule.thresholdCount(), highAmountRule.enabled());
        // As of Phase 10, a rule update is maker-checker gated (docs/adr/0011-maker-checker.md,
        // "configuration changes") — a DIFFERENT COMPLIANCE_OFFICER/ADMIN must approve it.
        String checkerToken = mintToken(UUID.randomUUID(), "rule-checker@meridianbank.local", "COMPLIANCE_OFFICER");
        try {
            ApprovalRequestResponse disableRequest = restTemplate.exchange("/api/v1/fraud-rules/{id}",
                    HttpMethod.PATCH, bearer(adminToken, disableIt), ApprovalRequestResponse.class,
                    highAmountRule.id()).getBody();
            assertThat(disableRequest.status()).isEqualTo("PENDING_APPROVAL");
            ResponseEntity<ApprovalRequestResponse> disableDecided = restTemplate.exchange(
                    "/api/v1/approvals/{id}/approve", HttpMethod.PATCH, bearer(checkerToken),
                    ApprovalRequestResponse.class, disableRequest.id());
            assertThat(disableDecided.getStatusCode()).isEqualTo(HttpStatus.OK);

            ResponseEntity<FraudRuleResponse> ruleNowDisabled = restTemplate.exchange("/api/v1/fraud-rules/{id}",
                    HttpMethod.GET, bearer(adminToken), FraudRuleResponse.class, highAmountRule.id());
            assertThat(ruleNowDisabled.getBody().enabled()).isFalse();

            ResponseEntity<RiskAssessmentResponse> response = restTemplate.exchange("/api/v1/risk-assessments",
                    HttpMethod.POST, bearer(paymentToken, paymentRequest(customerId, source, destination,
                            new BigDecimal("999999.00"))), RiskAssessmentResponse.class);

            // A huge amount to an already-known destination that would normally trip HIGH_AMOUNT
            // no longer does, because it's disabled; NEW_BENEFICIARY_HIGH_AMOUNT doesn't apply
            // (destination was seeded above), and no other default rule's condition is met here.
            assertThat(response.getBody().decision()).isEqualTo("ALLOW");
            assertThat(response.getBody().score()).isEqualTo(0);
        } finally {
            ApprovalRequestResponse restoreRequest = restTemplate.exchange("/api/v1/fraud-rules/{id}",
                    HttpMethod.PATCH, bearer(adminToken, restoreIt), ApprovalRequestResponse.class,
                    highAmountRule.id()).getBody();
            restTemplate.exchange("/api/v1/approvals/{id}/approve", HttpMethod.PATCH, bearer(checkerToken),
                    ApprovalRequestResponse.class, restoreRequest.id());
        }
    }

    @Test
    void ruleUpdate_riskAnalystCannotApproveEvenAsADifferentPerson() {
        // A RISK_ANALYST may view fraud rules but the checker role for FRAUD_RULE_UPDATE is
        // restricted to COMPLIANCE_OFFICER/ADMIN — see ApprovalAuthorization.
        String adminToken = mintToken(UUID.randomUUID(), "admin2@meridianbank.local", "ADMIN");
        ResponseEntity<FraudRuleResponse[]> rules = restTemplate.exchange("/api/v1/fraud-rules", HttpMethod.GET,
                bearer(adminToken), FraudRuleResponse[].class);
        FraudRuleResponse rule = rules.getBody()[0];
        UpdateFraudRuleRequest noOpUpdate = new UpdateFraudRuleRequest(rule.weight(), rule.thresholdNumeric(),
                rule.thresholdWindowSeconds(), rule.thresholdCount(), rule.enabled());

        ApprovalRequestResponse request = restTemplate.exchange("/api/v1/fraud-rules/{id}", HttpMethod.PATCH,
                bearer(adminToken, noOpUpdate), ApprovalRequestResponse.class, rule.id()).getBody();

        String riskAnalystToken = mintToken(UUID.randomUUID(), "analyst4@meridianbank.local", "RISK_ANALYST");
        ResponseEntity<ErrorResponse> response = restTemplate.exchange("/api/v1/approvals/{id}/approve",
                HttpMethod.PATCH, bearer(riskAnalystToken), ErrorResponse.class, request.id());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // Clean up: reject the no-op request with an authorized checker so it doesn't linger PENDING.
        String checkerToken = mintToken(UUID.randomUUID(), "rule-checker2@meridianbank.local", "COMPLIANCE_OFFICER");
        restTemplate.exchange("/api/v1/approvals/{id}/reject", HttpMethod.PATCH, bearer(checkerToken),
                ApprovalRequestResponse.class, request.id());
    }

    @Test
    void riskSummary_reflectsRealAssessmentHistory() {
        String paymentToken = mintToken(UUID.randomUUID(), "payment-service-caller@example.com", "CUSTOMER");
        UUID customerId = UUID.randomUUID();
        restTemplate.exchange("/api/v1/risk-assessments", HttpMethod.POST,
                bearer(paymentToken, paymentRequest(customerId, UUID.randomUUID(), UUID.randomUUID(),
                        new BigDecimal("10.00"))), RiskAssessmentResponse.class);
        restTemplate.exchange("/api/v1/risk-assessments", HttpMethod.POST,
                bearer(paymentToken, paymentRequest(customerId, UUID.randomUUID(), UUID.randomUUID(),
                        new BigDecimal("9000.00"))), RiskAssessmentResponse.class);

        String complianceToken = mintToken(UUID.randomUUID(), "compliance3@meridianbank.local", "COMPLIANCE_OFFICER");
        ResponseEntity<RiskSummaryResponse> summary = restTemplate.exchange("/api/v1/customers/{id}/risk-summary",
                HttpMethod.GET, bearer(complianceToken), RiskSummaryResponse.class, customerId);

        assertThat(summary.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(summary.getBody().assessmentCount()).isEqualTo(2);
        // $9000 to a brand-new destination trips both HIGH_AMOUNT (40) and
        // NEW_BENEFICIARY_HIGH_AMOUNT (25) — see highAmountAssessment_... above for the same math.
        assertThat(summary.getBody().highestScoreSeen()).isEqualTo(65);
    }

    /**
     * This service's auth boundary had no automated regression test at all before Phase 17 — see
     * docs/security/security-architecture.md, "Implementation status (Phase 17)".
     */
    @Test
    void protectedEndpoint_rejectsMissingGarbledAndExpiredTokens() {
        ResponseEntity<ErrorResponse> noToken = restTemplate.getForEntity("/api/v1/fraud-alerts", ErrorResponse.class);
        assertThat(noToken.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        HttpHeaders garbageHeaders = new HttpHeaders();
        garbageHeaders.set(HttpHeaders.AUTHORIZATION, "Bearer not-a-real-jwt");
        ResponseEntity<ErrorResponse> garbledToken = restTemplate.exchange("/api/v1/fraud-alerts",
                HttpMethod.GET, new HttpEntity<>(garbageHeaders), ErrorResponse.class);
        assertThat(garbledToken.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        Instant past = Instant.now().minusSeconds(3600);
        String expiredToken = Jwts.builder()
                .issuer("test")
                .subject(UUID.randomUUID().toString())
                .claim("email", "expired@example.com")
                .claim("role", "RISK_ANALYST")
                .issuedAt(Date.from(past.minusSeconds(900)))
                .expiration(Date.from(past))
                .signWith(SIGNING_KEY)
                .compact();
        ResponseEntity<ErrorResponse> expired = restTemplate.exchange("/api/v1/fraud-alerts",
                HttpMethod.GET, bearer(expiredToken), ErrorResponse.class);
        assertThat(expired.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
