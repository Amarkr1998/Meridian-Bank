# ADR-0009: API Gateway as Single Entry Point

**Status:** Accepted
**Date:** 2026-09-04

## Problem

With nine services, clients (the two React portals) need a stable, single point of contact rather
than knowing the address of every service, and cross-cutting concerns (JWT validation, rate
limiting, correlation ID assignment, CORS) need one consistent place to live rather than being
reimplemented per service.

## Decision

Introduce `api-gateway` as the single entry point for both portals. It handles routing to downstream
services, JWT validation (with role/identity context propagated downstream), Redis-backed rate
limiting, correlation ID generation/propagation, and CORS/secure headers. It performs coarse-grained
edge authorization only — fine-grained, resource-level authorization remains the responsibility of
each downstream service (see [ADR-0010](0010-rbac.md)).

## Alternatives Considered

- **No gateway — clients call services directly** — rejected: pushes CORS, auth, and rate limiting
  into every service redundantly, and exposes internal service topology to the frontend.
- **Gateway as the sole authorization boundary** — rejected: a gateway-only authorization model
  means a compromised or misconfigured gateway rule exposes every service; defense in depth requires
  each service to independently verify a caller's rights to the specific resource requested.

## Trade-offs

Gains: single client-facing contract, centralized cross-cutting concerns, simpler frontend
integration. Costs: the gateway is a single point of failure/bottleneck if not scaled appropriately
(mitigated by keeping it stateless and horizontally scalable — see
[docs/architecture/deployment-architecture.md](../architecture/deployment-architecture.md)).

## Consequences

- The gateway must remain stateless so it can run multiple replicas behind the ingress.
- "Backend is the final authorization boundary" (see
  [Security Architecture](../security/security-architecture.md)) means gateway-level checks are a
  UX/defense-in-depth layer, not a substitute for service-level checks.
- Implemented incrementally: basic routing from Phase 2 onward as services come online; full
  rate-limiting/JWT validation lands with Phase 2 (Auth/IAM).

## Implementation notes (Phase 14)

The incremental rollout described above didn't happen — `api-gateway` stayed an unbuilt README
stub through Phases 2–13, and every service independently verifies JWTs itself (which is still
correct: see "Alternatives Considered" above — a gateway-only authorization model was never the
plan). The gap surfaced when Phase 14 (React Customer Banking Portal) needed to decide how the
frontend reaches the backend; `api-gateway` was then actually built, immediately ahead of the
frontend that needs it, rather than continuously since Phase 2.

**Technology:** a plain Spring Boot MVC (servlet-stack) application — a small ordered chain of
`OncePerRequestFilter`s (CORS → correlation ID → Redis-backed rate limiting → edge JWT check →
reverse-proxy forward via `RestClient`) — not Spring Cloud Gateway. No other service in this
project uses the reactive/WebFlux stack, and adopting Spring Cloud's gateway starter would pull in
a second web stack plus its own BOM/version-compatibility surface for what this project actually
needs: prefix-based routing, not the fuller predicate/filter DSL Spring Cloud Gateway offers.

**Scope (Phase 14):** routed only the five services the customer portal calls (`auth-service`,
`customer-kyc-service`, `account-service`, `payment-service`, `notification-service`).
`fraud-risk-service`, `ledger-service`, and `audit-service` were unrouted, and `/api/v1/approvals`
was deliberately unrouted too, since it's duplicated across four gating services with no way to
disambiguate by path alone (see [ADR-0011](0011-maker-checker.md)).

## Implementation notes (Phase 15)

Extended the routing table for the Operations & Compliance Portal: `fraud-risk-service`
(`fraud-rules`, `fraud-alerts`, `aml-alerts`), `ledger-service` (`ledger/**`, including
`reconciliation-records`), and `audit-service` (`audit-events`) are now routed. Two things
remained deliberately unrouted for the same path-collision reasoning as `/approvals`:
`POST /api/v1/risk-assessments` (payment-service's own server-to-server call — no UI reaches it)
and `GET /api/v1/customers/{id}/risk-summary` (collides with the broader `/api/v1/customers`
prefix; nothing in this phase's route list needed it).

The `/approvals` ambiguity is now resolved: `RouteDefinition` gained an optional `rewritePrefix`,
letting a route match one path at the edge and forward a *different* path downstream. Four new
routes — `/api/v1/ops/approvals/accounts|kyc|fraud|payments` — each rewrite to that one gating
service's real `/api/v1/approvals`, so the ops portal gets a single unified-looking approvals
surface while each backend still only ever sees its own unchanged path.

A twelfth ops screen, System Health, has no single downstream service to answer "is everything
up" — `/api/v1/ops/system-health` is handled locally by a real controller in the gateway itself
(a route with a `null` base URL, recognized by `ProxyFilter` as "don't proxy"), fanning out
`/actuator/health` to all eight downstream services. It's also the one place this gateway decodes
and trusts a JWT's `role` claim, since there's no downstream service to defer that specific check
to — everything proxied still gets zero claim-trust, per this ADR's "Alternatives Considered."

See [services/api-gateway/README.md](../../services/api-gateway/README.md) for the full routing
table and live verification.

**Edge JWT check is real but intentionally shallow:** signature and expiry only, via the same
shared HS256 secret every service already verifies against — no claims are trusted or propagated
in a new header; the original `Authorization` header is forwarded unchanged, and each downstream
service still independently re-verifies it and enforces its own RBAC/resource-ownership rules.
This is the "Alternatives Considered" defense-in-depth stance made concrete, not a departure from
it. See [services/api-gateway/README.md](../../services/api-gateway/README.md) for the full
routing table and live verification.
