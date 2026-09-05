# Testing Strategy

> **Implementation status (Phase 17):** the backend testing approach described below (unit +
> Testcontainers integration tests) was already real and in place from Phase 2 onward — every
> phase's working rules required tests before considering that phase done, and every service
> README documents its own suite's exact count and what it proves. Phase 17 didn't invent that; it
> did three genuinely new things — closed a real gap (the frontend had zero automated tests), found
> and fixed a real gap in backend coverage (four services had no test proving their auth boundary
> actually rejects an unauthenticated request), and did an explicit, documented security-testing
> pass across the whole system rather than leaving that coverage implicit and scattered. See
> "What Phase 17 added" below for exactly what changed and why.

## The testing pyramid, as actually built

**Unit tests** (JUnit 5 + Mockito) — one per service-layer class, testing business rules and state
machines in isolation with collaborators mocked. E.g. `KycServiceTest`'s state-machine gating,
`ApprovalServiceTest`'s `approve_byTheMakerThemselves_isRejected`, `IdempotencyServiceTest`'s
claim/replay logic.

**Integration tests** (Spring Boot Test + Testcontainers) — one `*ControllerIntegrationTest` per
service, run against a **real** Postgres (and Redis, and Kafka, where the service uses them) via
Testcontainers, never mocked databases. This is where this project's most load-bearing proofs live:
real optimistic/pessimistic locking under genuine concurrent load (`ledger-service`), a genuine
concurrent-duplicate-request race against real Redis (`payment-service`), real maker-checker
self-approval rejection over real HTTP, and — new in Phase 11/13 — a real Kafka broker proving a
published event is genuinely consumed, deduplicated, retried, and dead-lettered
(`audit-service`, `notification-service`). Every one of these is described in detail in its
owning service's own README — this document doesn't re-derive those counts; they drift too fast
for a second document to track reliably, so treat each service's README as the source of truth for
"how many tests, testing what."

**Live-stack verification** — beyond JUnit entirely: at the end of nearly every phase in this
project, real `curl` sequences were run against the actual running 9-to-14-container
`docker compose` stack (not TestRestTemplate, not Testcontainers) to prove the feature works
end-to-end against genuinely persisted state. Every phase's completion report and every service's
README documents its own live-verification transcript. This is this project's substitute for a
staging environment.

## What Phase 17 added

### 1. Frontend test suite (a real gap, now closed)

The `frontend` had zero automated tests through Phases 14–16. Phase 17 added Vitest +
React Testing Library + `axios-mock-adapter` and 30 tests across:

- `tokenStorage` — round-trip save/load/clear, corrupt-JSON handling
- `RequireRole` (`RequireStaff`/`RequireCustomer`/`isStaffRole`) — every portal-redirect branch
- `AuthContext` — login (both the direct and MFA-required branches), `verifyMfa`, logout-even-if-
  the-server-call-fails
- `apiClient` — the security-relevant one: Authorization header attachment, the transparent
  401-refresh-and-retry flow (including that concurrent 401s share a single in-flight refresh
  rather than each firing their own), session-clearing when the refresh token itself is rejected,
  and `ApiError` wrapping of the standard error envelope
- `StatusChip`/`Money` — presentation logic (label mapping, currency formatting, null handling)

A real environment issue was found and fixed along the way: this workstation's non-ASCII home
directory path (`기현송`) broke Vitest's default `forks` worker pool the same way it broke the JDK
Mockito inline mock maker for the backend (see `services/api-gateway/README.md`'s "Environment
note") — switched to `pool: 'threads'` in `vite.config.ts`. `npm audit` reports 0 vulnerabilities.

See [frontend/README.md](../../frontend/README.md) for how to run these (`npm test`).

### 2. Backend auth-boundary gap (found and fixed)

Auditing existing coverage turned up something concrete: only `api-gateway` and 4 of the 9 backend
services (`audit-service`, `auth-service`, `ledger-service`, `notification-service`) had any test
asserting a `401` for an unauthenticated request. **`customer-kyc-service`, `account-service`,
`payment-service`, and `fraud-risk-service` had zero tests proving their auth boundary actually
rejects a request with no token, a garbled token, or an expired token** — every existing test in
those four assumed a request carried *some* valid token and tested authorization (role, ownership)
from there, never authentication itself. Added one `protectedEndpoint_rejectsMissingGarbledAndExpiredTokens`
test per service (all four now pass against a real Postgres+Redis, real signed-but-expired JWTs
included, not just a missing header) — see each service's `*ControllerIntegrationTest`.

### 3. Gateway security-header gap (found and fixed)

Every backend service gets baseline security response headers (`X-Content-Type-Options: nosniff`,
`X-Frame-Options: DENY`, cache control on sensitive responses) for free from Spring Security's
defaults. `api-gateway` has no Spring Security at all (see its README, "Why not Spring Cloud
Gateway"), so its own directly-generated responses — every `401`/`403`/`404`/`429`/`503` error
envelope, and `SystemHealthController`'s own success response — shipped without them. A proxied
response was never affected (it already carries the downstream service's own headers, forwarded
unchanged). Fixed in `ErrorResponseWriter.applyBaselineSecurityHeaders`, applied to every
edge-generated response; regression-tested in `GatewayProxyIntegrationTest`.

### 4. Injection / secret-logging audit (checked, nothing found)

Grepped the entire backend for native/raw SQL (`createNativeQuery`, `@Query(nativeQuery`,
`JdbcTemplate`) and string-concatenated `@Query` values — none exist anywhere; every query goes
through Spring Data JPA repository methods or parameterized JPQL, which is injection-safe by
construction. Grepped every log statement for `password`/`token`/`secret`/`otp`/`accountNumber` —
the only match is a comment in `AdminBootstrapRunner` explicitly confirming the password is *not*
logged. Consistent with CLAUDE.md's non-negotiable masking rules, verified rather than assumed.

### 5. Dependency freshness (checked; full CVE scanning found infeasible here)

`mvn versions:display-dependency-updates` was run against `api-gateway` (representative of all
nine services, since every one of them pins the identical `spring-boot-starter-parent:3.5.16` and
the same `jjwt`/`mockito` versions via the same convention) — every dependency is already on its
latest *stable* line; the only "updates" available are pre-release milestones
(`spring-boot-starter-*: 3.5.16 -> 4.2.0-M1`), which is not something to adopt.

**OWASP Dependency-Check was attempted and found infeasible in this environment**, not skipped
without trying: `org.owasp:dependency-check-maven:12.1.0:check` requires a first-time NVD
(National Vulnerability Database) data sync that, without an NVD API key, is rate-limited hard
enough to take a very long time (potentially much longer than this session's budget) or fail
outright — confirmed by a direct attempt (`NoDataException: Autoupdate is disabled and the
database does not exist` when sync is skipped; a real sync attempt did not complete in a
reasonable window). Recorded here honestly rather than silently omitted: a real CVE-level
dependency scan is a legitimate gap in this project's security testing, and running one (with an
NVD API key, or via `npm audit`'s backend equivalent like GitHub's Dependabot/Snyk once this
repository is actually hosted on GitHub) would be the natural next step.

## Planned Implementation Phase

Phase 17 (Testing + Testcontainers + Security Testing), building on the per-phase testing
discipline already in place since Phase 2.
