package com.meridianbank.gateway.routing;

import java.util.List;

/**
 * One downstream service's routing entry: the path prefixes it owns under {@code /api/v1/},
 * its base URL, and which of those paths are public (no edge JWT check — mirrors that service's
 * own {@code SecurityConfig} permit-all list exactly, so the edge and the service never disagree
 * about what's public).
 *
 * <p>{@code rewritePrefix}, when set, replaces the matched gateway-facing prefix with a different
 * prefix on the actual downstream service before forwarding — see {@link #rewritePath}. This
 * exists solely for the four maker-checker gating services (account-service,
 * customer-kyc-service, fraud-risk-service, payment-service), which all expose the identical path
 * {@code /api/v1/approvals} for entirely different resources — undisambiguatable by path alone.
 * Each gets its own gateway-only alias prefix (e.g. {@code /api/v1/ops/approvals/accounts}) that
 * rewrites to that service's real {@code /api/v1/approvals} — see RouteRegistry.
 */
public record RouteDefinition(String serviceName, String baseUrl, List<String> prefixes, List<String> publicPatterns,
                               String rewritePrefix) {

    public RouteDefinition(String serviceName, String baseUrl, List<String> prefixes, List<String> publicPatterns) {
        this(serviceName, baseUrl, prefixes, publicPatterns, null);
    }

    public boolean matchesPrefix(String path) {
        return prefixes.stream().anyMatch(prefix -> path.equals(prefix) || path.startsWith(prefix + "/"));
    }

    /** No-op unless {@link #rewritePrefix} is set, in which case the matched gateway prefix is replaced by it. */
    public String rewritePath(String path) {
        if (rewritePrefix == null) {
            return path;
        }
        String matchedPrefix = prefixes.stream()
                .filter(prefix -> path.equals(prefix) || path.startsWith(prefix + "/"))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Path does not match any prefix: " + path));
        return rewritePrefix + path.substring(matchedPrefix.length());
    }
}
