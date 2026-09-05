# api-gateway

**Status:** Implemented (Phase 14, built immediately ahead of the React Customer Banking Portal
that needs it — see [ADR-0009](../../docs/adr/0009-api-gateway.md), "Implementation notes").
Phase 15 extended the routing table for the Operations & Compliance Portal and added the
`/approvals` alias mechanism and a system-health aggregator.

## Responsibility

Single entry point for client traffic. Owns:

- Request routing to downstream services, by `/api/v1/...` path prefix
- CORS (browsers can't call nine different ports directly)
- Correlation ID generation/propagation — the first genuine edge-to-service correlation ID in
  this project; every downstream service already honors an incoming `X-Correlation-Id` header, so
  one ID now threads through the whole call chain from a single request
- Redis-backed rate limiting, per client IP, fixed window
- Coarse-grained edge JWT validation (signature + expiry only) on non-public routes — **not** the
  authorization boundary; every downstream service still independently re-verifies the same token
  and enforces its own RBAC/resource-ownership rules (defense in depth, per ADR-0009)

## Why not Spring Cloud Gateway

This is a plain Spring Boot MVC (servlet-stack) app — a small ordered chain of
`OncePerRequestFilter`s, not Spring Cloud Gateway. No other service in this project uses the
reactive/WebFlux stack, and this gateway's actual job (prefix-based routing, CORS, an edge JWT
check, a rate limiter) doesn't need Spring Cloud Gateway's fuller predicate/filter DSL — adopting
it would mean a second web stack plus its own BOM/version-compatibility surface for no real gain
here. See `ProxyFilter`, which forwards a request to its resolved downstream service via `RestClient`
and relays the response back byte-for-byte.

## Request pipeline

Five filters, in this order (see each filter's `@Order`):

1. **`CorsFilter`** (0) — answers a preflight `OPTIONS` directly (204, never reaches later filters
   or a downstream service); adds `Access-Control-*` headers to every other response, including
   the error responses the filters below write.
2. **`CorrelationIdFilter`** (1) — reuses an incoming `X-Correlation-Id` or mints one; forwarded
   downstream by `ProxyFilter`.
3. **`RateLimitFilter`** (2) — Redis `INCR`+`EXPIRE` fixed window keyed by client IP
   (`X-Forwarded-For` first, else remote address); `429 RATE_LIMIT_EXCEEDED` over the ceiling.
   Fails open on a Redis error — a rate limiter outage shouldn't take the edge down with it.
4. **`EdgeAuthFilter`** (3) — for any route this gateway knows about that isn't in that route's
   public list (see Routing table below), requires a syntactically valid, correctly-signed,
   unexpired bearer token, else `401 UNAUTHENTICATED`. A path this gateway doesn't route at all is
   deliberately let through here — `ProxyFilter` is what turns it into `404`, not this filter.
5. **`ProxyFilter`** (4, terminal) — forwards method, headers (minus hop-by-hop ones), query
   string, and body to the resolved service's base URL (rewriting the path first if the matched
   route has a `rewritePrefix` — see "Approval alias routing" below), then relays status, headers,
   and body back unchanged. `503 UPSTREAM_UNAVAILABLE` if the downstream call fails;
   `404 ROUTE_NOT_FOUND` for an unmatched `/api/v1/...` path. A route with a `null` base URL
   (`/api/v1/ops/system-health`, Phase 15) is instead handled locally, by a real
   `@RestController` in this gateway — `ProxyFilter` just defers to normal Spring dispatch once
   `EdgeAuthFilter` has confirmed a valid token. Bounded connect/read timeouts
   (`meridian.gateway.upstream.*`) so a stuck downstream degrades to a fast error instead of
   hanging a gateway thread.

Anything outside `/api/v1/...` (this gateway's own `/actuator/health`) falls through to normal
Spring dispatch.

## Routing table

| Path prefix | Service | Public (no edge token required) |
|---|---|---|
| `/api/v1/auth/**` | auth-service | `register`, `login`, `mfa/verify`, `refresh`, `logout`, `password-reset/**` |
| `/api/v1/customers/**`, `/api/v1/kyc/**`, `/api/v1/support-requests/**` | customer-kyc-service | `customers/register`, `customers/*/verify-contact`, `customers/*/resend-verification` |
| `/api/v1/accounts/**`, `/api/v1/beneficiaries/**` | account-service | none |
| `/api/v1/payments/**` | payment-service | none |
| `/api/v1/notifications/**` | notification-service | none |
| `/api/v1/fraud-rules/**`, `/api/v1/fraud-alerts/**`, `/api/v1/aml-alerts/**` | fraud-risk-service | none |
| `/api/v1/ledger/**` (postings, balances, entries, `reconciliation-records`) | ledger-service | none |
| `/api/v1/audit-events/**` | audit-service | none |
| `/api/v1/ops/approvals/accounts\|kyc\|fraud\|payments` (rewrites to `/api/v1/approvals` — see below) | account/customer-kyc/fraud-risk/payment-service | none |
| `/api/v1/ops/system-health` (handled locally, not proxied — see below) | *(this gateway)* | none |

The public list mirrors each service's own `SecurityConfig` permit-all list exactly, so the edge
and the service never disagree about what's public — see `RouteRegistry`'s Javadoc.

**Deliberately not routed:** `POST /api/v1/risk-assessments` (created only by payment-service's own
server-to-server call into fraud-risk-service — no UI ever calls it) and
`GET /api/v1/customers/{id}/risk-summary` (collides with the broader `/api/v1/customers` prefix
already owned by customer-kyc-service; nothing in the current ops route list needs a dedicated
per-customer risk-summary screen — a future phase can alias it the same way `/approvals` was
solved, if needed).

### Approval alias routing (Phase 15)

`/api/v1/approvals` is duplicated verbatim across four independent gating services — see
[ADR-0011](../../docs/adr/0011-maker-checker.md), "Implementation notes" — with no way to tell
them apart by path alone. `RouteDefinition` gained an optional `rewritePrefix`: a route can match
one path at the edge and forward a *different* path downstream. Four routes exploit this —
`/api/v1/ops/approvals/accounts` (etc.) match only that literal alias and rewrite it to
`/api/v1/approvals` on that one service's base URL. The ops portal calls the alias; nothing
downstream ever sees anything but its own real `/api/v1/approvals` path.

### System health (Phase 15)

`/api/v1/ops/system-health` has no single downstream service that could answer "is everything
up," so it's handled by a real `SystemHealthController` living in this gateway (see
`RouteRegistry`'s `null`-baseUrl entry and `ProxyFilter`'s handling of it), fanning out a
`GET /actuator/health` to all eight downstream services via the same `RestClient` used for
proxying. It's also the one place this gateway decodes and trusts a JWT's `role` claim
(`JwtVerifier#decodeRole`) — the sole exception to "no claims are trusted here," documented
in its Javadoc, made necessary because there's no downstream service to defer that check to.

## Running locally

From the repository root: `docker compose up -d --build api-gateway` (brings up `redis` and every
routed service automatically). Listens on `localhost:8080` (`API_GATEWAY_PORT` in `.env`).
Health: `GET /actuator/health`. CORS origin(s) allowed: `MERIDIAN_GATEWAY_CORS_ALLOWED_ORIGINS`
(default `http://localhost:5173`, the frontend's Vite dev server).

## Tests

- `RouteRegistryTest` — pure unit tests for the routing table: prefix resolution across all eight
  routed services (including that `/api/v1/accounts` doesn't prefix-match an unrelated
  `/api/v1/accountsomethingelse`), the deliberately-unrouted paths, public-path matching per route,
  the approval-alias rewrite (`rewritePath`) translating each alias to the real `/api/v1/approvals`
  path, and that `/api/v1/ops/system-health` resolves to a route with a `null` base URL.
- `GatewayProxyIntegrationTest` — **the flagship proof for this service**: a real embedded gateway,
  a real Redis (Testcontainers), and a plain JDK `HttpServer` standing in for a downstream service
  (not a mocked Spring bean, not an in-process fake router). Proves: CORS preflight is answered
  without ever reaching the stub; a public path is forwarded with no token; a protected path is
  rejected `401` with no token and forwarded — with the `Authorization` header and body intact —
  once a validly-signed token is presented; a malformed token is rejected `401`; an unrouted
  `/api/v1/...` path `404`s at the gateway itself; the Redis-backed rate limiter genuinely trips
  after its configured ceiling (each test uses its own synthetic `X-Forwarded-For` IP so the
  shared-by-IP rate-limit bucket doesn't make the suite order-dependent); a correlation ID minted
  at the edge for a request that never carried one genuinely reaches the downstream service — this
  one caught a real bug during development: `CorrelationIdFilter` only records the ID it mints in
  a request attribute and this gateway's own response header, never on the (immutable) incoming
  request itself, so `ProxyFilter` has to add it explicitly when forwarding or it silently never
  propagates — fixed and now regression-tested; an `/api/v1/ops/approvals/fraud/42/approve` call
  genuinely arrives at the stub as `/api/v1/approvals/42/approve` (the alias rewrite, proven
  end-to-end, not just at the `RouteDefinition` unit level); and `/api/v1/ops/system-health`
  returns `200` with every service's status for a staff role and `403` for a customer role.

20/20 tests pass locally.

Live-verified against the real 12-service stack (all 11 backend services + this gateway): staff
login through the gateway, then real reads across every newly-routed Phase 15 service
(`fraud-alerts`, `aml-alerts`, `fraud-rules`, `reconciliation-records`, `audit-events`) with
correct per-endpoint RBAC (an `OPERATIONS` token got a genuine `403` on `/audit-events`, which
requires `AUDITOR`/`COMPLIANCE_OFFICER`/`ADMIN`); all four `/api/v1/ops/approvals/*` aliases
returned real historical approval requests (`BLOCK_ACCOUNT`, `CUSTOMER_STATUS_CHANGE`,
`FRAUD_ALERT_RESOLUTION`, `RELEASE_PAYMENT`) from their respective services, proving the rewrite
against genuine backends, not just the test stub; `/api/v1/ops/system-health` returned real `UP`
statuses for all nine downstream services and correctly `403`'d a customer token; and a customer
token was correctly rejected `403` on a staff-only path, confirming portal isolation end-to-end.

**Environment note:** this workstation's Mockito inline mock maker fails to self-attach under the
locally-installed JDK (Temurin 25) — a pre-existing local-machine issue, not something introduced
here, and unrelated to this service's own logic (a plain JUnit/Mockito unit test with no Spring
context still worked, just with a self-attach warning). `src/test/resources/mockito-extensions/org.mockito.plugins.MockMaker`
pins Mockito to the reflection-based `mock-maker-subclass` for this module only, which sidesteps
the JVM-agent self-attach path entirely and doesn't affect anything this service's tests actually
mock. Worth revisiting for the other eight services if the same failure shows up there.
