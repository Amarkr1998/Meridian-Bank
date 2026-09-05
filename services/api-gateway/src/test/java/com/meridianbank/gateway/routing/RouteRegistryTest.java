package com.meridianbank.gateway.routing;

import com.meridianbank.gateway.config.GatewayServiceUrls;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RouteRegistryTest {

    private final RouteRegistry registry = new RouteRegistry(new GatewayServiceUrls(
            "http://auth", "http://kyc", "http://account", "http://payment", "http://notification",
            "http://fraud", "http://ledger", "http://audit"));

    @Test
    void resolve_matchesOwningService() {
        assertThat(registry.resolve("/api/v1/auth/login")).get()
                .extracting(RouteDefinition::serviceName).isEqualTo("auth-service");
        assertThat(registry.resolve("/api/v1/customers/123")).get()
                .extracting(RouteDefinition::serviceName).isEqualTo("customer-kyc-service");
        assertThat(registry.resolve("/api/v1/kyc/456/approve")).get()
                .extracting(RouteDefinition::serviceName).isEqualTo("customer-kyc-service");
        assertThat(registry.resolve("/api/v1/support-requests")).get()
                .extracting(RouteDefinition::serviceName).isEqualTo("customer-kyc-service");
        assertThat(registry.resolve("/api/v1/accounts/requests")).get()
                .extracting(RouteDefinition::serviceName).isEqualTo("account-service");
        assertThat(registry.resolve("/api/v1/beneficiaries")).get()
                .extracting(RouteDefinition::serviceName).isEqualTo("account-service");
        assertThat(registry.resolve("/api/v1/payments")).get()
                .extracting(RouteDefinition::serviceName).isEqualTo("payment-service");
        assertThat(registry.resolve("/api/v1/notifications/1/read")).get()
                .extracting(RouteDefinition::serviceName).isEqualTo("notification-service");
        assertThat(registry.resolve("/api/v1/fraud-rules")).get()
                .extracting(RouteDefinition::serviceName).isEqualTo("fraud-risk-service");
        assertThat(registry.resolve("/api/v1/fraud-alerts/1")).get()
                .extracting(RouteDefinition::serviceName).isEqualTo("fraud-risk-service");
        assertThat(registry.resolve("/api/v1/aml-alerts/1/escalate")).get()
                .extracting(RouteDefinition::serviceName).isEqualTo("fraud-risk-service");
        assertThat(registry.resolve("/api/v1/ledger/accounts/1/balance")).get()
                .extracting(RouteDefinition::serviceName).isEqualTo("ledger-service");
        assertThat(registry.resolve("/api/v1/ledger/reconciliation-records")).get()
                .extracting(RouteDefinition::serviceName).isEqualTo("ledger-service");
        assertThat(registry.resolve("/api/v1/audit-events")).get()
                .extracting(RouteDefinition::serviceName).isEqualTo("audit-service");
    }

    @Test
    void resolve_doesNotPrefixMatchUnrelatedResources() {
        // "/api/v1/accounts" must not swallow "/api/v1/accountsomethingelse" style paths
        assertThat(registry.resolve("/api/v1/accountsomethingelse")).isEmpty();
    }

    @Test
    void resolve_ambiguousApprovalsPathIsDeliberatelyUnrouted() {
        assertThat(registry.resolve("/api/v1/approvals")).isEmpty();
    }

    @Test
    void resolve_stillDeliberatelyUnroutedInPhase15() {
        // no UI ever POSTs a risk assessment directly — only payment-service does, server-to-server
        assertThat(registry.resolve("/api/v1/risk-assessments")).isEmpty();
        // collides with the broader /api/v1/customers prefix already owned by customer-kyc-service
        assertThat(registry.resolve("/api/v1/customers/123/risk-summary")).get()
                .extracting(RouteDefinition::serviceName).isEqualTo("customer-kyc-service");
    }

    @Test
    void resolve_approvalAliases_routeToTheCorrectGatingService() {
        assertThat(registry.resolve("/api/v1/ops/approvals/accounts")).get()
                .extracting(RouteDefinition::serviceName).isEqualTo("account-service");
        assertThat(registry.resolve("/api/v1/ops/approvals/kyc/1/approve")).get()
                .extracting(RouteDefinition::serviceName).isEqualTo("customer-kyc-service");
        assertThat(registry.resolve("/api/v1/ops/approvals/fraud/1/reject")).get()
                .extracting(RouteDefinition::serviceName).isEqualTo("fraud-risk-service");
        assertThat(registry.resolve("/api/v1/ops/approvals/payments")).get()
                .extracting(RouteDefinition::serviceName).isEqualTo("payment-service");
    }

    @Test
    void rewritePath_translatesTheAliasToTheRealApprovalsPath() {
        RouteDefinition accountApprovals = registry.resolve("/api/v1/ops/approvals/accounts").orElseThrow();
        assertThat(accountApprovals.rewritePath("/api/v1/ops/approvals/accounts")).isEqualTo("/api/v1/approvals");
        assertThat(accountApprovals.rewritePath("/api/v1/ops/approvals/accounts/42/approve"))
                .isEqualTo("/api/v1/approvals/42/approve");

        // ordinary routes are untouched (no rewritePrefix configured)
        RouteDefinition accounts = registry.resolve("/api/v1/accounts").orElseThrow();
        assertThat(accounts.rewritePath("/api/v1/accounts/42")).isEqualTo("/api/v1/accounts/42");
    }

    @Test
    void resolve_systemHealthIsHandledLocallyByTheGatewayItself() {
        RouteDefinition systemHealth = registry.resolve("/api/v1/ops/system-health").orElseThrow();
        assertThat(systemHealth.baseUrl()).isNull();
        assertThat(registry.isPublic(systemHealth, "/api/v1/ops/system-health")).isFalse();
    }

    @Test
    void isPublic_matchesOnlyDocumentedPublicPaths() {
        RouteDefinition auth = registry.resolve("/api/v1/auth/login").orElseThrow();
        assertThat(registry.isPublic(auth, "/api/v1/auth/login")).isTrue();
        assertThat(registry.isPublic(auth, "/api/v1/auth/register")).isTrue();
        assertThat(registry.isPublic(auth, "/api/v1/auth/password-reset/confirm")).isTrue();
        assertThat(registry.isPublic(auth, "/api/v1/auth/refresh")).isTrue();
        assertThat(registry.isPublic(auth, "/api/v1/auth/sessions")).isFalse();

        RouteDefinition kyc = registry.resolve("/api/v1/customers/register").orElseThrow();
        assertThat(registry.isPublic(kyc, "/api/v1/customers/register")).isTrue();
        assertThat(registry.isPublic(kyc, "/api/v1/customers/123e4567-e89b-12d3-a456-426614174000/verify-contact")).isTrue();
        assertThat(registry.isPublic(kyc, "/api/v1/customers/123e4567-e89b-12d3-a456-426614174000")).isFalse();
        assertThat(registry.isPublic(kyc, "/api/v1/kyc")).isFalse();

        RouteDefinition account = registry.resolve("/api/v1/accounts").orElseThrow();
        assertThat(registry.isPublic(account, "/api/v1/accounts")).isFalse();

        RouteDefinition approvalsAlias = registry.resolve("/api/v1/ops/approvals/accounts").orElseThrow();
        assertThat(registry.isPublic(approvalsAlias, "/api/v1/ops/approvals/accounts")).isFalse();
    }
}
