package com.meridianbank.gateway.routing;

import com.meridianbank.gateway.config.GatewayServiceUrls;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;

import java.util.List;
import java.util.Optional;

/**
 * The gateway's static routing table. Route topology (which prefixes belong to which service,
 * and which of those are public) is code, not runtime config — same reasoning as every other
 * service's hardcoded {@code SecurityConfig} permit-all list, which this mirrors path-for-path;
 * only each service's base URL is externalized (see {@link GatewayServiceUrls}).
 *
 * <p><b>Phase 14</b> added the five services the customer portal calls (auth, customer-kyc,
 * account, payment, notification). <b>Phase 15</b> (Operations & Compliance Portal) added
 * fraud-risk-service, ledger-service, and audit-service.
 *
 * <p>{@code /api/v1/approvals} is duplicated verbatim across four gating services
 * (account-service, customer-kyc-service, fraud-risk-service, payment-service — see
 * ADR-0011 "Implementation notes") with no way to disambiguate by path alone. Phase 15 resolves
 * this with four gateway-only alias prefixes under {@code /api/v1/ops/approvals/<service>} that
 * rewrite to each service's real {@code /api/v1/approvals} — see {@link RouteDefinition#rewritePath}.
 * The ops portal calls the alias; nothing downstream changes.
 *
 * <p><b>Still deliberately unrouted:</b> {@code POST /api/v1/risk-assessments} (created only by
 * payment-service's own server-to-server call into fraud-risk-service during risk checks — no UI
 * ever calls it) and {@code GET /api/v1/customers/{id}/risk-summary} (a second, narrower path
 * collision: it lives under the same {@code /api/v1/customers} prefix already routed to
 * customer-kyc-service, and nothing in Phase 15's ops route list specifically calls for a
 * per-customer risk summary screen — a future phase can add a disambiguating alias the same way
 * {@code /approvals} was solved, if it turns out to be needed).
 */
@Component
public class RouteRegistry {

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private final List<RouteDefinition> routes;

    public RouteRegistry(GatewayServiceUrls urls) {
        this.routes = List.of(
                new RouteDefinition(
                        "auth-service", urls.authService(),
                        List.of("/api/v1/auth"),
                        List.of(
                                "/api/v1/auth/register",
                                "/api/v1/auth/login",
                                "/api/v1/auth/mfa/verify",
                                "/api/v1/auth/refresh",
                                "/api/v1/auth/logout",
                                "/api/v1/auth/password-reset/**"
                        )
                ),
                new RouteDefinition(
                        "customer-kyc-service", urls.customerKycService(),
                        List.of("/api/v1/customers", "/api/v1/kyc", "/api/v1/support-requests"),
                        List.of(
                                "/api/v1/customers/register",
                                "/api/v1/customers/*/verify-contact",
                                "/api/v1/customers/*/resend-verification"
                        )
                ),
                new RouteDefinition(
                        "account-service", urls.accountService(),
                        List.of("/api/v1/accounts", "/api/v1/beneficiaries"),
                        List.of()
                ),
                new RouteDefinition(
                        "payment-service", urls.paymentService(),
                        List.of("/api/v1/payments"),
                        List.of()
                ),
                new RouteDefinition(
                        "notification-service", urls.notificationService(),
                        List.of("/api/v1/notifications"),
                        List.of()
                ),
                new RouteDefinition(
                        "fraud-risk-service", urls.fraudRiskService(),
                        List.of("/api/v1/fraud-rules", "/api/v1/fraud-alerts", "/api/v1/aml-alerts"),
                        List.of()
                ),
                new RouteDefinition(
                        "ledger-service", urls.ledgerService(),
                        List.of("/api/v1/ledger"),
                        List.of()
                ),
                new RouteDefinition(
                        "audit-service", urls.auditService(),
                        List.of("/api/v1/audit-events"),
                        List.of()
                ),
                // --- /api/v1/approvals aliases (Phase 15) — see class Javadoc ---
                new RouteDefinition(
                        "account-service", urls.accountService(),
                        List.of("/api/v1/ops/approvals/accounts"),
                        List.of(),
                        "/api/v1/approvals"
                ),
                new RouteDefinition(
                        "customer-kyc-service", urls.customerKycService(),
                        List.of("/api/v1/ops/approvals/kyc"),
                        List.of(),
                        "/api/v1/approvals"
                ),
                new RouteDefinition(
                        "fraud-risk-service", urls.fraudRiskService(),
                        List.of("/api/v1/ops/approvals/fraud"),
                        List.of(),
                        "/api/v1/approvals"
                ),
                new RouteDefinition(
                        "payment-service", urls.paymentService(),
                        List.of("/api/v1/ops/approvals/payments"),
                        List.of(),
                        "/api/v1/approvals"
                ),
                // A null baseUrl means "handled locally by a real @RestController in this
                // gateway" (see ProxyFilter) rather than proxied — EdgeAuthFilter still requires
                // a valid token since this route isn't public. See SystemHealthController.
                new RouteDefinition(
                        "api-gateway", null,
                        List.of("/api/v1/ops/system-health"),
                        List.of()
                )
        );
    }

    public Optional<RouteDefinition> resolve(String path) {
        return routes.stream().filter(route -> route.matchesPrefix(path)).findFirst();
    }

    public boolean isPublic(RouteDefinition route, String path) {
        return route.publicPatterns().stream().anyMatch(pattern -> PATH_MATCHER.match(pattern, path));
    }
}
