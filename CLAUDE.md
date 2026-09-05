# CLAUDE.md — Meridian Bank

This file guides any Claude Code session working in this repository. It is the durable source of
truth for how this project is built. Read it before starting work.

## What This Is

**Meridian Digital Banking & Payments Platform** — a fictional, portfolio-quality enterprise digital
banking system demonstrating Java/Spring Boot microservices, event-driven architecture, a
double-entry ledger, fraud/AML/maker-checker controls, and a professional React banking UI.

Product names: **Meridian Digital Banking & Payments Platform** (product), **Meridian Digital
Banking** (customer portal), **Meridian Operations & Compliance** (internal portal).
Tagline: *Secure Banking. Trusted by Design.*

This must never read as a student CRUD project. Every phase should reinforce security, correctness,
auditability, reliability, and maintainability as first-class concerns.

## Non-Negotiable Safety Rules

- **Never** use real customer, banking, payment, KYC, or identity data. All data is synthetic and
  clearly marked as demo/synthetic.
- **Never** store real card information or connect to real payment networks or banking systems.
- **Never** claim regulatory certification or production banking compliance. The AML module in
  particular must be documented as a simplified educational implementation only.
- **Never** log passwords, OTPs, JWTs, secrets, full account numbers, or private keys. Mask sensitive
  values (e.g. `Account: ********4589`).
- External systems (payment networks, SMS/email, external bank feeds) are always mocked/simulated.

## Working Rules (apply to every phase)

1. Inspect the existing repository before starting — understand current architecture, reuse existing
   code, don't overwrite working functionality unnecessarily.
2. Implement **only** the requested phase. Do not scaffold ahead into later phases' business logic.
3. Compile the project and run relevant tests; fix failures before considering a phase done.
4. Verify integration with what already exists.
5. Update documentation to reflect what changed.
6. Report exactly what changed at the end of a phase.
7. Never leave the repository intentionally broken.
8. Do not advance to the next major phase automatically — stop and wait to be told to proceed.

## Technology Stack

**Backend:** Java 21, Spring Boot 3.x, Spring Web, Spring Security, Spring Data JPA, Hibernate, Bean
Validation, Maven, Flyway, PostgreSQL, Apache Kafka, Redis, Resilience4j, Micrometer, Spring Boot
Actuator, OpenAPI/Swagger. Lombok only where it improves readability; MapStruct where useful.

**Frontend:** React, TypeScript, Vite, React Router, TanStack Query, Axios/Fetch, React Hook Form,
Zod, Material UI, Recharts. Must look like a real commercial banking application — professional
navy/blue and neutral palette, clean typography, no generic Bootstrap-admin or flashy fintech look.

**Testing:** JUnit 5, Mockito, Spring Boot Test, Testcontainers, REST integration tests. Cover
business logic, security/authorization, concurrency, and idempotency, not just happy paths.

**Infrastructure:** Docker, Docker Compose, Kubernetes (Minikube/Kind), GitHub Actions, Prometheus,
Grafana. Everything must run locally without paid cloud services.

## Microservices (fixed set — do not add more to inflate scope)

1. `api-gateway` — edge routing, auth propagation, rate limiting
2. `auth-service` — IAM: login, JWT, refresh tokens, MFA, RBAC
3. `customer-kyc-service` — registration, profile, KYC review workflow
4. `account-service` — account opening workflow, balances, statements
5. `payment-service` — payment orchestration, idempotency, transaction limits
6. `ledger-service` — double-entry ledger, balance derivation
7. `fraud-risk-service` — fraud rules, risk scoring, AML monitoring
8. `notification-service` — in-app/email/SMS (simulated) notifications
9. `audit-service` — append-only audit trail

REST for synchronous calls needing an immediate response; Kafka for asynchronous/event-driven
workflows. Full architecture: [docs/architecture](docs/architecture).

## Core Banking Invariants

- **Double-entry ledger.** Every successful financial transaction produces balanced debit/credit
  ledger entries. Never implement a transfer as a bare `balance = balance - amount`.
- **Idempotency.** Payment-mutating endpoints require an `Idempotency-Key`; a repeated key returns
  the original result rather than reprocessing. Never blindly retry financial requests without it.
- **Concurrency safety.** Use correct transaction boundaries, isolation levels, and optimistic/
  pessimistic locking as appropriate; concurrent transfers must not corrupt balances or double-spend.
- **Outbox pattern.** Business state changes and their outbound Kafka events are written in the same
  DB transaction; a publisher relays outbox rows to Kafka so events are never lost on broker downtime.
- **RBAC + resource-level authorization** on every endpoint. Backend authorization is the final
  security boundary — a customer must never reach another customer's data by changing an ID.
- **Maker-checker (four-eyes)** on sensitive operations (high-value approval, account block/unblock,
  customer status changes, config changes, high-risk fraud decisions). The maker can never approve
  their own request; every decision is recorded in the audit trail.
- **Audit everything sensitive**, append-only, actor + role + action + resource + timestamp + result +
  correlation ID. Normal business users cannot modify audit history.
- **API errors** follow a consistent envelope (`code`, `message`, `correlationId`) under `/api/v1/...`.
  Never leak stack traces to clients.

## Roles (RBAC)

`CUSTOMER`, `OPERATIONS`, `COMPLIANCE_OFFICER`, `RISK_ANALYST`, `AUDITOR`, `ADMIN`

## Implementation Phases

Build in this exact order; do not skip ahead. Each phase ends with a compiled, tested, documented
state and an explicit stop.

| Phase | Scope |
|---|---|
| 0 | Architecture + repository + documentation *(done)* |
| 1 | Infrastructure: Docker + PostgreSQL + Kafka + Redis *(done)* |
| 2 | Auth/IAM + JWT + RBAC + MFA *(done)* |
| 3 | Customer Registration + Onboarding + KYC *(done)* |
| 4 | Account Opening + Account Management *(done)* |
| 5 | Beneficiary Management *(done)* |
| 6 | Payment Processing + Idempotency *(done)* |
| 7 | Double-Entry Ledger + Concurrency *(done)* |
| 8 | Kafka + Outbox + Retry + DLQ *(done)* |
| 9 | Fraud + Risk + AML *(done)* |
| 10 | Maker-Checker + Governance *(done)* |
| 11 | Audit + Data Governance *(done)* |
| 12 | Reconciliation + Batch Processing *(done)* |
| 13 | Notification + Customer Support *(done)* |
| 14 | React Customer Banking Portal *(done)* |
| 15 | React Operations & Compliance Portal *(done)* |
| 16 | Observability + Prometheus + Grafana *(done)* |
| 17 | Testing + Testcontainers + Security Testing *(done)* |
| 18 | Docker + Kubernetes *(done — current)* |
| 19 | CI/CD |
| 20 | Final documentation + diagrams + demo data + interview prep |

**Status: Phase 0 through 18 complete.** Repository scaffolding, documentation, local infrastructure
(PostgreSQL, Kafka, Redis), `auth-service` (JWT, RBAC, MFA, lockout, password reset, session
management), `customer-kyc-service` (registration, contact verification, profile, KYC review
workflow), `account-service` (account opening request workflow gated on KYC, account lifecycle,
configurable limits, beneficiary management, and — as of Phase 7 — real balance display sourced
from `ledger-service`), `payment-service` (account-to-account transfers with mandatory idempotency
— proven atomic against real Redis, including a genuine concurrent-request race test — and, as of
Phase 7, real ledger posting), and `ledger-service` (a real double-entry ledger with
pessimistic-locking, fixed-lock-ordering concurrency control — proven against real Postgres under
genuine concurrent load, both preventing overdrafts and preventing deadlock between opposite-
direction concurrent transfers) are in place and verified end-to-end against live containers,
including the full chain from registration through a real payment that genuinely moves money
between two customers' real balances. As of Phase 8, `customer-kyc-service`, `account-service`,
and `payment-service` each write to their own transactional outbox (same DB transaction as the
business change) and run a scheduled publisher that relays PENDING rows to a real Kafka broker —
`customer.created`, `kyc.updated`, `account.created`, `account.approved`, `payment.initiated`,
`payment.completed`, and `payment.failed` are genuinely published events, proven against a real
Postgres and a real Kafka broker (Testcontainers) and against the live docker-compose stack (read
back with `kafka-console-consumer`), not simulated. A publish failure retries with exponential
backoff; exhausting the attempt budget marks the outbox row FAILED and best-effort routes the same
envelope to a `<eventType>.DLQ` topic — see `OutboxPublisher`'s Javadoc in any of the four services
for the exact policy (`fraud-risk-service` uses it too, as of Phase 9). This was a **producer-side**
retry/DLQ only as of Phase 8-9 — no Kafka consumer existed yet anywhere in the system (see the
Phase 11 paragraph below for `audit-service`, this project's first one). As of Phase 9, `payment-service`
calls a real `fraud-risk-service` synchronously during `RISK_CHECK` — configurable weighted fraud
rules (amount, velocity, new-beneficiary, repeat-risk, rapid-sequential, all editable business data
per docs/governance/governance-principles.md) score every payment 0-100 and return
`ALLOW`/`REVIEW`/`BLOCK`, which `payment-service` genuinely enforces (`BLOCK` and `REVIEW` both fail
the payment, with distinct failure codes — see payment-service's README for why `REVIEW` doesn't yet
place a payment on hold); independent AML signals (high-value, velocity, structuring,
repeated-beneficiary) raise a separate, non-blocking compliance queue. Proven live: an escalating
sequence of real payments moved from `ALLOW` through `REVIEW` to `BLOCK` as real risk history
accumulated in fraud-risk-service's own database, a real `RISK_ANALYST` staff account reviewed and
confirmed the resulting fraud alert over real RBAC-gated HTTP, and every alert genuinely published
`fraud.detected` to the live Kafka broker — see [Getting Started](README.md#getting-started),
[services/auth-service](services/auth-service),
[services/customer-kyc-service](services/customer-kyc-service),
[services/account-service](services/account-service),
[services/payment-service](services/payment-service),
[services/ledger-service](services/ledger-service), and
[services/fraud-risk-service](services/fraud-risk-service). As of Phase 10, five sensitive
operations are genuinely gated behind server-enforced two-person maker-checker approval — account
blocking (`account-service`), customer status changes (`customer-kyc-service`), fraud alert
resolution and fraud rule updates (`fraud-risk-service`), and release of a REVIEW-held payment
(`payment-service`): a maker creates a `PENDING_APPROVAL` request, and only a *genuinely different*
staff member can decide it — self-approval is rejected server-side
(`403 SELF_APPROVAL_NOT_ALLOWED`) even called directly against the API, not merely hidden in a UI.
Each of the four gating services owns its own duplicated `approval_requests` table and
`ApprovalService` (no shared/centralized approvals service, matching this project's no-shared-DB
convention — see [ADR-0011](docs/adr/0011-maker-checker.md)). Only a single decision round is
implemented (APPROVE/REJECT, no "return for revision" cycle). Proven both by live-HTTP integration
tests in all four services and by a live run against the actual nine-service docker-compose stack:
in every one of the four flows, the maker's own approval attempt was rejected
(`403 SELF_APPROVAL_NOT_ALLOWED`) and a different staff member's approval genuinely executed the
gated action — including payment-service's release flow, where a real $9,000 REVIEW-held payment
was released by a different `RISK_ANALYST` checker, creating and processing a brand-new
transaction that genuinely succeeded and moved the source account's real balance
($50,000.00 → $41,000.00 via `ledger-service`), while the original transaction stayed
untouched, and fraud-risk-service's rule-update gate additionally confirmed its
action-specific checker restriction live (a `RISK_ANALYST` — a valid alert-resolution checker —
was correctly rejected as a rule-update checker). See
[docs/architecture/maker-checker-flow.md](docs/architecture/maker-checker-flow.md) and each gating
service's README for exactly what it gates and what remains single-actor (e.g. account
freeze/close and beneficiary block/unblock are deliberately not gated). As of Phase 11,
`audit-service` is real and is this project's first genuine Kafka *consumer* (previously every
service was producer-only — see docs/architecture/kafka-architecture.md): it consumes `audit.event`
under its own consumer group, idempotently by the Kafka envelope's `eventId` (backstopped by a
unique DB constraint), retries a failed message with exponential backoff, and dead-letters an
exhausted message to `audit.event.DLQ`. `customer-kyc-service`, `account-service`,
`payment-service`, and `fraud-risk-service` each now publish `audit.event` (same outbox pattern as
their other events, via a small duplicated `audit` package per service) for their sensitive
actions — registration, KYC decisions, account/beneficiary events, payments, fraud/AML alert
creation, and every maker-checker `APPROVAL_CREATED`/`APPROVAL_COMPLETED` decision (plus a
`CONFIGURATION_CHANGED` event specifically when a fraud rule update actually applies) — see
[services/audit-service/README.md](services/audit-service/README.md) for the exact action catalog
per service and, just as importantly, what's honestly not covered yet (this is a real pipeline for
a defined action set, not exhaustive audit coverage of the whole system). Proven live against the
running ten-service stack: a fresh customer registration, KYC submission/approval, an account
opening approval, a maker-checker account block (both the request and the decision), a real
payment, and a real fraud alert were each independently confirmed to have landed as real rows in
`audit-service`'s own database, queryable over RBAC-gated HTTP by a provisioned `AUDITOR` account,
with a `CUSTOMER` token correctly rejected `403` on the same endpoint. There is no update or delete
path anywhere in `audit-service`, for any role — proven by a live test that even an `ADMIN` token
gets `405 METHOD_NOT_ALLOWED` on `POST /audit-events`. As of Phase 12, `ledger-service` genuinely
reconciles every real ledger transaction against a synthetic external counterpart on a scheduled
job — no new microservice; reconciliation lives inside `ledger-service` itself, since
`ledger_entries` (the internal source of truth it compares against) already lives there and this
project's fixed 9-service list has no separate reconciliation service. The external feed is
deterministic and reproducible (a pure function of each transaction's own id — see
`ExternalFeedGenerator`'s Javadoc), not pure randomness: roughly 70% of transactions get an
immediate exact match, 20% a match delayed by a configurable window (genuinely resolving
`PENDING` → `MATCHED` once wall-clock time passes, not a second random roll), and 10% a
deliberately wrong amount, landing `MISMATCHED` for staff to investigate and resolve
(`OPERATIONS`/`ADMIN`-gated `MISMATCHED` → `INVESTIGATION` → `RESOLVED`). This is also
`ledger-service`'s first use of both the outbox pattern (`reconciliation.completed`) and the
Phase 11 audit pattern (`RECONCILIATION_MISMATCH_DETECTED`, `RECONCILIATION_RESOLVED`, both
genuinely reaching `audit-service`). Reconciliation never mutates `ledger_entries` — its entire
footprint is the new `reconciliation_records`/`external_transactions` tables. Proven live against
the running stack: three real postings crafted to land in each classification band were correctly
classified `MATCHED`/`MISMATCHED`/`PENDING` by the real scheduled job on its next tick (not called
directly); the `PENDING` one was independently confirmed to genuinely transition to `MATCHED`
exactly 120 seconds later, purely from wall-clock time passing, on the job's next tick; the
`MISMATCHED` one was claimed and resolved by a real `OPERATIONS` staff member (an `AUDITOR` token
correctly rejected `403` attempting to claim it), with both the detection and the resolution
genuinely reaching `audit-service` as real rows. As of Phase 13, `notification-service` is real —
this project's second genuine Kafka consumer, after `audit-service` — consuming `payment.completed`,
`payment.failed`, `kyc.updated`, `account.approved`, `fraud.detected`, and `notification.requested`
under its own consumer group, same idempotent-by-`eventId` + retry/DLQ policy as `audit-service`.
Every consumed event becomes a real, queryable in-app notification (`GET /api/v1/notifications`,
self-service, resource-ownership enforced); simulated email/SMS delivery is logged only, since
this service has no real email address or phone number to send to. Two of the originally-scoped
notification types, `LOGIN_ALERT`/`SECURITY_ALERT`, remain defined-but-unreachable — `auth-service`
has no Kafka wiring at all, and retrofitting it wasn't in scope for this phase's actual business
logic. Customer Support — the other half of this phase — was added inside the existing
`customer-kyc-service` (no new container): `support_requests` ownership, left open since Phase 0,
is finalized there (a ticket is fundamentally tied to the customer raising it), with a genuine
`OPEN` → `IN_PROGRESS` → `RESOLVED` staff workflow (not maker-checker gated), its own
`SUPPORT_REQUEST_CREATED`/`SUPPORT_REQUEST_RESOLVED` audit events (reusing the existing Phase 11
`audit` package), and — on resolution — this project's first real publish to
`notification.requested`, the generic topic that had existed unused in the catalog since Phase 8.
Proven live against the real eleven-service stack: a real KYC approval, a real account approval,
and a real resolved support request each produced the expected `KYC_STATUS_CHANGED`,
`ACCOUNT_STATUS_CHANGED`, and `SUPPORT_REQUEST_RESOLVED` notification for a fresh customer, with
the support-ticket's audit events confirmed landed in `audit-service` by resource ID; two real
payments against the project's one funded account (whose fraud/risk history has accumulated across
every prior phase's live testing) both failed risk assessment rather than succeeding, genuinely
exercising `PAYMENT_FAILED` and seven independent `FRAUD_ALERT` notifications from one `BLOCK`
decision's several AML signals (`PAYMENT_SUCCESS` itself wasn't produced live this pass, for lack
of an unused funded account, but is covered by `NotificationEventListenerTest`); and RBAC was
independently confirmed live — a customer reading or marking-read another customer's notification
was rejected `403`, while `OPERATIONS` staff filtering by `customerId` succeeded. There is also no
funding/deposit endpoint anywhere in the system yet (by design — see
ledger-service's README, "Known limitation: no funding source"), so every account genuinely starts
at zero. As of Phase 14, `api-gateway` and the customer-facing `frontend` are both real.
`api-gateway` was built as a prerequisite for this phase (see
[ADR-0009](docs/adr/0009-api-gateway.md), "Implementation notes," for why it wasn't built
incrementally from Phase 2 as originally planned) — a plain Spring Boot MVC reverse proxy (not
Spring Cloud Gateway, to avoid a second web stack for no real gain — see its README), routing the
five customer-facing services with CORS, a Redis-backed per-IP rate limiter, edge JWT validation
(signature/expiry only — full RBAC stays the responsibility of each downstream service, per
ADR-0009's defense-in-depth stance), and correlation-ID propagation genuinely threading through
the whole call chain for the first time in this project. `/api/v1/approvals` and
fraud-risk-service/ledger-service/audit-service are deliberately unrouted — nothing in Phase 14
needs them, and `/approvals`' ambiguity (duplicated across four gating services) is a Phase 15
problem. The `frontend` is a React/TypeScript/Vite customer portal (`Meridian Digital Banking`)
covering the full customer journey: registration, KYC submission, account opening, beneficiaries,
payments, transaction history, a client-composed statements view (no PDF/CSV export exists),
support tickets, notifications, profile, and security/session management — calling only
`api-gateway`, never a service port directly. Built against MUI `^6.5.0`, deliberately not the
newer `9.x` available in this registry, because v9 changed `Stack`/`Typography`/`Grid`'s
polymorphic typing in ways that would have meant rewriting this phase's component usage against an
unfamiliar, newly-released major — see frontend/README.md. Both were verified together: `tsc -b`
and `npm run build` both clean, and a live `curl` sequence reproducing the frontend's exact API
calls through the real gateway against the real 11-service backend — login, MFA verify, protected
reads across all five routed services (with response shapes matching the frontend's TypeScript
types exactly), a `401` for a missing token, a `404` for the deliberately-unrouted
`/api/v1/approvals`, and a real payment POST whose replayed `Idempotency-Key` returned the same
transaction rather than reprocessing, proving header forwarding works against genuine
payment-service idempotency, not just a test stub. A real bug was caught and fixed by the
gateway's own test suite along the way: `CorrelationIdFilter` only records a freshly-minted
correlation ID in a request attribute and the gateway's own response header, never on the
(immutable) incoming request, so a request with no client-supplied ID was silently failing to
propagate it downstream until `ProxyFilter` was fixed to set it explicitly — now regression-tested
live (fixed value confirmed identical on both the gateway's response and the value the downstream
stub genuinely received). **Not verified: actual browser rendering** — this environment has no
headless-browser/screenshot tool, so no page was ever visually inspected or clicked through; see
frontend/README.md's "Verification performed" for exactly what that gap means.

As of Phase 15, the Operations & Compliance portal is real too, in the same `frontend`
codebase/build as originally scoped (not a second app) — `RootRedirect` and `RequireRole.tsx`
route a signed-in user to `/dashboard` or `/ops/dashboard` purely by role, and each portal's
routes actively redirect the other kind of user away rather than merely hiding a nav link.
`api-gateway`'s routing table was extended to fraud-risk-service (`fraud-rules`, `fraud-alerts`,
`aml-alerts`), ledger-service (`ledger/**` including `reconciliation-records`), and audit-service
(`audit-events`); `POST /api/v1/risk-assessments` and `GET /api/v1/customers/{id}/risk-summary`
stayed deliberately unrouted for the same path-collision reasoning as `/approvals` (the latter
collides with the already-routed `/api/v1/customers` prefix; neither has a UI caller in this
phase's route list). The `/api/v1/approvals` collision itself is now genuinely solved:
`RouteDefinition` gained an optional path-rewrite, and four new aliases
(`/api/v1/ops/approvals/accounts|kyc|fraud|payments`) each rewrite to that one gating service's
real `/api/v1/approvals` before forwarding — proven live by reading back genuine historical
`BLOCK_ACCOUNT`, `CUSTOMER_STATUS_CHANGE`, `FRAUD_ALERT_RESOLUTION`, and `RELEASE_PAYMENT`
approval records through all four aliases against the real backends, not a synthetic fixture. A
twelfth ops screen, System Health, is answered by a small real controller living in the gateway
itself — no single downstream service can answer "is everything up" — fanning out
`GET /actuator/health` to all eight downstream services; it's also the one place this gateway
decodes and trusts a JWT's `role` claim (`JwtVerifier#decodeRole`), the sole exception to "no
claims are trusted here," made necessary because there's no downstream service to defer that one
check to. The ops portal itself covers Dashboard (cross-queue summary counts), Transactions
(all-customer payment view), Fraud (alert queue + maker-checker-gated rule configuration, tabbed),
AML (alert queue, direct actions — not maker-checker gated, a narrower compliance-only RBAC
surface than fraud per fraud-risk-service's own README), KYC Review, Accounts (opening-request
queue + freeze/unfreeze/close/limits/maker-checker-gated block, tabbed), Customers
(maker-checker-gated status changes), Approvals (unified across all four gating services' queues),
Reconciliation, Audit Trail (read-only, search-filtered), Support, and System Health. Verified
live the same way as Phase 14: every newly-routed endpoint read through the gateway with a real
staff token, per-endpoint RBAC confirmed correct (an `OPERATIONS` token genuinely `403`'d on
`/audit-events`, which requires `AUDITOR`/`COMPLIANCE_OFFICER`/`ADMIN`), and a customer token
confirmed `403`'d on a staff-only path — portal isolation proven, not just assumed.
**Not verified: actual browser rendering**, same gap as Phase 14 — see frontend/README.md.

This completes this project's fixed scope of 9 backend services + 2 portals in 1 frontend
codebase. As of Phase 16, every one of those nine services plus `api-gateway` genuinely exposes
Prometheus metrics: `micrometer-registry-prometheus` added to each service's `pom.xml`,
`/actuator/prometheus` exposed and added to each service's existing permit-all list alongside
`/actuator/health`/`/actuator/info` (see [ADR-0015](docs/adr/0015-observability.md) for why an
unauthenticated metrics endpoint is an acceptable trade-off in this local/demo network trust
boundary), and every metric tagged `application: ${spring.application.name}`. A single Prometheus
container (`infrastructure/prometheus/prometheus.yml`) scrapes all ten targets every 15 seconds; a
single Grafana container auto-provisions that Prometheus instance as its datasource and auto-loads
one hand-built dashboard (`infrastructure/grafana/dashboards/meridian-overview.json` — service
up/down, HTTP request rate, HTTP 5xx rate, HTTP p95 latency, JVM heap, HikariCP active
connections, JVM live threads, each split per service) rather than importing a community
dashboard by ID, avoiding an external dependency on a specific grafana.com dashboard staying
compatible. No custom business metrics were added — deliberately: this phase wires up the
standard, free Spring Boot/Micrometer auto-instrumentation across ten services, which is already
substantial and is what "Observability + Prometheus + Grafana" means as infrastructure; named
business counters (payment volume, fraud alert rate, etc.) would require touching business logic
in most services and are a natural, separate follow-on, not silently expanded scope. Verified live
against the real 14-container stack: `http://localhost:9090/api/v1/targets` shows all ten scrape
jobs `up` with no errors; Grafana's Prometheus datasource and "Meridian Bank — Service Overview"
dashboard are both genuinely provisioned, confirmed via Grafana's own API (not just by reading the
provisioning config files); and direct Prometheus queries for HTTP request rate, JVM heap used,
and HikariCP active connections each returned real, distinct values per service, not empty
results. See [infrastructure/prometheus/README.md](infrastructure/prometheus/README.md) and
[infrastructure/grafana/README.md](infrastructure/grafana/README.md).

As of Phase 17, this project has a documented, audited testing posture rather than an implicit
one — see [docs/testing/testing-strategy.md](docs/testing/testing-strategy.md) for the full
detail. The per-service unit/Testcontainers-integration/live-stack-verification discipline
described there was already real from Phase 2 onward (every phase's working rules required tests
before considering it done); Phase 17's own additions are three genuinely new things, not a
restatement of what already existed. **First:** the `frontend` had zero automated tests through
Phases 14–16 — closed with Vitest + React Testing Library + `axios-mock-adapter`, 30 tests
covering `tokenStorage`, the portal-redirect guards (`RequireStaff`/`RequireCustomer`), the full
`AuthContext` login/MFA/logout lifecycle, and — the security-relevant one — `apiClient`'s
transparent 401-refresh-and-retry interceptor (including that concurrent 401s share one in-flight
refresh rather than each triggering their own, and that a rejected refresh token clears the
session and notifies exactly once). A real environment issue surfaced and was fixed along the way:
this workstation's non-ASCII home directory path broke Vitest's default `forks` worker pool the
same way it broke the JDK/Mockito inline mock maker for the backend — fixed by switching to
`pool: 'threads'`. **Second:** auditing existing backend coverage found that only `api-gateway`
and 4 of the 9 backend services had any test proving a request with no/garbled/expired token gets
rejected — `customer-kyc-service`, `account-service`, `payment-service`, and `fraud-risk-service`
had zero such coverage; every existing test in those four assumed a request already carried some
valid token. Fixed with one new auth-boundary test per service, all four passing against a real
Postgres+Redis. **Third:** `api-gateway`, having no Spring Security at all, was shipping its own
directly-generated responses (every edge-rejected error, and `SystemHealthController`'s success
response) without the baseline security headers (`X-Content-Type-Options`, `X-Frame-Options`)
every other service gets for free from Spring Security's defaults — fixed in
`ErrorResponseWriter.applyBaselineSecurityHeaders` and regression-tested. Beyond fixes: the whole
backend was grepped for injection risk (none — no raw/native/string-concatenated SQL exists
anywhere; everything goes through parameterized Spring Data JPA) and for secret-leaking log
statements (none found), and dependency freshness was checked (every service already on the latest
*stable* Spring Boot line). **Honestly not done:** a real CVE-level dependency scan
(OWASP Dependency-Check) was attempted, not skipped — it requires a first-time NVD database sync
that isn't feasible without an API key in this environment (confirmed by a direct, timed attempt)
— recorded as a genuine, acknowledged gap rather than silently omitted. This completes this
project's fixed scope of 9 backend services + 2 portals + observability + a documented testing
posture.

As of Phase 18, the platform runs on plain Kubernetes manifests (no Helm/Kustomize), not just
docker-compose — see [infrastructure/kubernetes](infrastructure/kubernetes). The Docker half was
already complete (every service already had a per-service multi-stage Dockerfile from its own
build phase); Phase 18's actual new work was the Kubernetes manifest set: the
`meridian-bank` namespace/ConfigMap/Secret, `postgres` and `kafka` StatefulSets (headless Services,
`volumeClaimTemplates`), a `redis` Deployment, one Deployment+Service per backend service plus
`api-gateway` (`payment-service` and `api-gateway` at 2 replicas), a `kafka-init` topic-bootstrap
Job, HorizontalPodAutoscalers for `api-gateway`/`payment-service`, Prometheus+Grafana Deployments
reusing the exact scrape config and dashboard from Phase 16, and a single Ingress routing only to
`api-gateway` (per ADR-0009). Every Kubernetes Service is named identically to its docker-compose
counterpart, so every `*_BASE_URL` value and every Prometheus/Grafana config file is byte-for-byte
identical between the two deployment targets — no application code changed for Kubernetes.

This was verified against a real, live cluster (Docker Desktop's built-in single-node Kubernetes,
4 CPU / ~8Gi memory) — applied and iterated on until genuinely healthy, not just applied and
assumed working. Every pod this manifest set creates reached real `Ready`/`Complete` state, and
`api-gateway`'s `/actuator/health` was confirmed reachable end-to-end through a real
`kubectl port-forward`
(`{"status":"UP","groups":["liveness","readiness"]}`). That live testing caught four real bugs
invisible from a static YAML review: (1) Kafka's and Postgres's exec-based probes (`kafka-broker-
api-versions.sh`, `pg_isready`) inherited Kubernetes' default 1-second exec-probe timeout, nowhere
near enough for a subprocess spawn under real contention — kubelet was killing a genuinely healthy
Kafka broker and, more seriously, the actual Postgres database, purely from a timeout artifact;
(2) per-service `requests.cpu` values summed to more than the node's real 4-CPU capacity once every
Deployment/StatefulSet was applied together, correctly triggering `FailedScheduling: Insufficient
cpu`; (3) every backend service's `livenessProbe` pointed at the same dependency-aggregated
`/actuator/health` used for readiness, so a transient Kafka blip cascaded into kubelet killing
unrelated, otherwise-healthy services — mitigated by widening `failureThreshold` rather than a full
liveness/readiness health-group split (which would need a Spring Security change and image rebuild
across all eight services), left as the more architecturally correct follow-up; and (4) a genuine,
100%-reproducible deadlock in Kafka's headless Service — this single combined broker+controller
node needs to resolve its own per-pod DNS name to register with its own controller quorum during
startup, but a headless Service only publishes a pod's DNS record once that pod is Ready, and the
pod could never become Ready without that registration succeeding first; fixed with
`publishNotReadyAddresses: true`, the standard pattern for self-referential StatefulSet members.
Bug (1)'s exact same root cause was independently confirmed live in docker-compose's own `kafka`
healthcheck under heavy host load and fixed there too (see the `healthcheck.timeout` comment on
`docker-compose.yml`'s `kafka` service) — not a Kubernetes-only finding. See
[infrastructure/kubernetes/README.md](infrastructure/kubernetes/README.md#real-bugs-found-and-fixed-during-live-verification)
for the full evidence per bug. One separately-observed, non-bug limitation: running the full
docker-compose stack and the full Kubernetes deployment simultaneously oversubscribes this single
4-CPU development machine, producing slow JVM boots and intermittent (self-recovering, not
deadlocking) pod restarts in whichever stack has less headroom at a given moment — an inherent
hardware constraint of this environment, not a manifest defect; verify one deployment target at a
time for a clean run. Remaining phases (19 onward: CI/CD, final documentation) harden and deliver
what already exists, not new product surface. Do not begin Phase 19 without explicit instruction.

## Documentation Map

- [docs/architecture](docs/architecture) — system/component diagrams, sequence flows (payment,
  onboarding, KYC, fraud, AML, maker-checker, reconciliation)
- [docs/api](docs/api) — API versioning, conventions, error format
- [docs/security](docs/security) — authN/authZ, data protection architecture
- [docs/governance](docs/governance) — data classification, retention, masking, maker-checker
- [docs/database](docs/database) — domain entities / ERD
- [docs/kafka](docs/kafka) — topic catalog & event contracts
- [docs/reconciliation](docs/reconciliation) — reconciliation design
- [docs/testing](docs/testing) — testing strategy across the whole system, Phase 17 findings
- [docs/adr](docs/adr) — Architecture Decision Records, one per major structural decision
