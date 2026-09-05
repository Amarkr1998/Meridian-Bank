# customer-kyc-service

**Status:** Implemented (Phase 3 — Customer Registration + Onboarding + KYC; Phase 8 wired real
Kafka event publishing in; Phase 10 gated customer status changes behind maker-checker approval;
Phase 11 wired real audit-trail publishing in; Phase 13 added customer support tickets).

Java 21, Spring Boot 3.5, Spring Security, Spring Data JPA, PostgreSQL (`customer_kyc_service`
database), Redis, Kafka, Flyway, Resilience4j. See [pom.xml](pom.xml).

## Responsibility

Customer profile and KYC review workflow — see
[docs/architecture/onboarding-flow.md](../../docs/architecture/onboarding-flow.md) and
[docs/architecture/kyc-flow.md](../../docs/architecture/kyc-flow.md).

Registration spans two services: [auth-service](../auth-service) owns the login identity (email +
password + role), this service owns the customer profile. `AuthServiceClient` calls
`POST /api/v1/auth/register` synchronously at registration time and the returned id becomes this
customer's id in both places — not a cross-database foreign key, just a matching UUID (see
[docs/adr/0003-postgresql-system-of-record.md](../../docs/adr/0003-postgresql-system-of-record.md)).
The call is wrapped in a circuit breaker (see
[docs/adr/0014-circuit-breaker.md](../../docs/adr/0014-circuit-breaker.md)) and is never
automatically retried, since registration isn't safely repeatable the way an idempotency-keyed
payment is.

- Self-service registration, combined email/mobile contact verification (OTP simulation)
- Customer profile (get/update — contact & address only; name/DOB are immutable post-registration)
- Customer status (`ACTIVE`/`INACTIVE`/`BLOCKED`/`SUSPENDED`) with an append-only change history
- KYC submission with document **metadata only** (never a real identity document)
- KYC review workflow: `KYC_PENDING` → `KYC_IN_REVIEW` → `KYC_VERIFIED`/`KYC_REJECTED`, append-only
  per submission (a resubmission after rejection creates a new record, never overwrites one)
- This service independently verifies JWTs issued by auth-service (shared HS256 secret) rather
  than trusting a gateway — see
  [docs/security/security-architecture.md](../../docs/security/security-architecture.md)

Automated risk scoring during KYC review is **not** wired into this service — `fraud-risk-service`
(Phase 9) exposes a real `GET /api/v1/customers/{id}/risk-summary` for this purpose, but per
[docs/architecture/kyc-flow.md](../../docs/architecture/kyc-flow.md) it's the (not-yet-built,
Phase 15) Operations Portal that would call it, not this service — see
[services/fraud-risk-service/README.md](../fraud-risk-service/README.md). Every decision in this
phase is made directly by a `COMPLIANCE_OFFICER`, with no automated risk input reaching this
service.

## Kafka events (Phase 8)

Publishes `customer.created` (on registration) and `kyc.updated` (on every KYC status transition —
submit, start-review, approve, reject) via the transactional outbox pattern — see
[docs/adr/0005-outbox-pattern.md](../../docs/adr/0005-outbox-pattern.md) and
[services/ledger-service](../ledger-service) sibling services for the identical `OutboxWriter`/
`OutboxPublisher` pattern (duplicated per service, same rationale as this project's JWT
verification). As of Phase 13, `kyc.updated` is genuinely consumed by
[notification-service](../notification-service) (`KYC_STATUS_CHANGED` notifications);
`customer.created` still has no consumer. See
[docs/kafka/topics.md](../../docs/kafka/topics.md).

## Audit trail (Phase 11)

Publishes `audit.event` (same transactional outbox pattern as every other event, via a small
duplicated `audit` package — `AuditAction`, `AuditEventPayload`, `AuditEventPublisher` — see
[docs/adr/0012-audit-architecture.md](../../docs/adr/0012-audit-architecture.md)) for
`CUSTOMER_REGISTERED` (registration), `KYC_SUBMITTED`, `KYC_APPROVED`, `KYC_REJECTED`, and
`APPROVAL_CREATED`/`APPROVAL_COMPLETED` (the maker-checker request/decision points above).
Consumed and durably stored by [audit-service](../audit-service), this project's first genuine
Kafka consumer. Not every endpoint emits one yet — e.g. profile updates (`PATCH /customers/{id}`)
don't — see audit-service's README for the full cross-service action catalog and what's
deliberately still out of scope.

## Maker-checker (Phase 10)

`PATCH /customers/{id}/status` is gated behind a two-person approval — see
[docs/adr/0011-maker-checker.md](../../docs/adr/0011-maker-checker.md) and
[docs/architecture/maker-checker-flow.md](../../docs/architecture/maker-checker-flow.md). The
endpoint now returns `202 Accepted` with an `ApprovalRequestResponse` instead of changing the
customer's status immediately: a maker (`OPERATIONS`/`ADMIN`) requests a target status + reason, and
a *different* staff member must decide it via `PATCH /api/v1/approvals/{id}/approve` or
`.../reject` before the status actually changes. `ApprovalService.requirePending` enforces
server-side that the checker cannot be the same user as the maker
(`403 SELF_APPROVAL_NOT_ALLOWED`). Unlike the other three gated services, this action's approval
row carries an extra `requestedStatus` field alongside `reason`, since the payload needs both. Only
a single decision round is implemented (no "return for revision" cycle).

## Customer Support (Phase 13)

Customer-raised support tickets — see
[docs/database/domain-model.md](../../docs/database/domain-model.md)'s "Implementation note
(Phase 13)": owned here because a support request is
fundamentally tied to the customer raising it, not to any particular account or transaction, and
this service already owns the `Customer` entity. Not maker-checker gated — resolving a ticket
isn't in [ADR-0011](../../docs/adr/0011-maker-checker.md)'s list of governance-sensitive actions,
so this is a normal single-actor staff action, same as a KYC review decision.

- **`OPEN` → `IN_PROGRESS` → `RESOLVED`, terminal** — a single decision round, same deliberate
  scope reduction as maker-checker's "no return for revision" (ADR-0011): a customer unsatisfied
  with a `RESOLVED` ticket raises a new one rather than reopening the old one.
- Self-service for customers (their own tickets only, via `SupportRequestController`'s
  `@resourceOwnership.isSupportRequestOwner` check); a general staff review queue for
  `OPERATIONS`/`ADMIN` to work, readable by the wider governance/oversight audience too.
- Creation and resolution both publish `audit.event` (`SUPPORT_REQUEST_CREATED`,
  `SUPPORT_REQUEST_RESOLVED`) via this service's existing `audit` package (Phase 11) — no new
  infrastructure needed, just reuse.
- **Resolving a ticket is this project's first real producer to `notification.requested`** — the
  generic topic that existed in the catalog specifically for this purpose since Phase 8, unused
  until now. [notification-service](../notification-service) (Phase 13's other half) consumes it
  and creates a real, queryable notification for the customer — see its README for exactly how.

## API

All endpoints under `/api/v1`. Standard error envelope on failure.

| Method & Path | Auth | Description |
|---|---|---|
| `POST /customers/register` | public | Registration (calls auth-service, then creates the profile) |
| `POST /customers/{id}/verify-contact` | public | Confirm the OTP sent at registration |
| `POST /customers/{id}/resend-verification` | public | Issue a fresh OTP |
| `GET /customers/{id}` | self or staff | Profile |
| `PATCH /customers/{id}` | self or staff (`OPERATIONS`/`ADMIN`) | Update contact/address |
| `GET /customers` | staff | Paginated list, filterable by status |
| `PATCH /customers/{id}/status` | staff (`OPERATIONS`/`ADMIN`) | **Maker-checker gated** — returns `202` with an approval request |
| `GET /customers/{id}/status-history` | self or staff | Status change history |
| `POST /customers/{id}/kyc` | self only | Submit KYC (requires verified contact) |
| `GET /customers/{id}/kyc` | self or staff | This customer's KYC submission history |
| `GET /kyc` | staff (`COMPLIANCE_OFFICER`/`ADMIN`/`AUDITOR`/`RISK_ANALYST`) | Review queue |
| `GET /kyc/{kycId}` | staff | Submission detail |
| `POST /kyc/{kycId}/start-review` | `COMPLIANCE_OFFICER`/`ADMIN` | Claim for review |
| `POST /kyc/{kycId}/approve` | `COMPLIANCE_OFFICER`/`ADMIN` | Approve |
| `POST /kyc/{kycId}/reject` | `COMPLIANCE_OFFICER`/`ADMIN` | Reject with reason |
| `GET /approvals` | `OPERATIONS`/`ADMIN`/`AUDITOR` | Review queue, filterable by `status` |
| `GET /approvals/{id}` | `OPERATIONS`/`ADMIN`/`AUDITOR` | Request detail |
| `PATCH /approvals/{id}/approve` | `OPERATIONS`/`ADMIN`, and ≠ the requesting maker | Approve — actually changes the customer's status |
| `PATCH /approvals/{id}/reject` | `OPERATIONS`/`ADMIN`, and ≠ the requesting maker | Reject — status stays as it was |

## Demo-mode note

Same pattern as auth-service's MFA: there's no real email/SMS channel until
`notification-service` (Phase 13), so in local/demo mode
(`meridian.security.contact-verification.demo-expose-otp`, default `true`) the OTP is returned
directly in the API response. Never written to logs; must never be enabled in a real deployment.

## Cross-service integration

Both [account-service](../account-service) (Phase 4, account opening) and
[payment-service](../payment-service) (Phase 6, per-payment KYC check) call
`GET /api/v1/customers/{id}/kyc` to check for a `KYC_VERIFIED` record, forwarding the customer's
own bearer token — see either service's README for why that satisfies this endpoint's
resource-ownership check without any change needed here.

## Running locally

From the repository root: `docker compose up -d --build customer-kyc-service` (brings up
`postgres`, `redis`, `auth-service`, and Kafka — waiting for the `kafka-init` topic bootstrap to
complete — automatically). Listens on `localhost:8082` (`CUSTOMER_KYC_SERVICE_PORT` in `.env`).
Health: `GET /actuator/health` (deliberately does not depend on Kafka reachability — see
`OutboxPublisher`'s Javadoc on why event publishing degrades independently).

## Tests

- `KycServiceTest` — unit tests (Mockito) for the KYC state machine and submission gating
  (contact must be verified; no duplicate in-flight submission), plus assertions that
  `kyc.updated` is published via `OutboxWriter` on submit and on approve.
- `CustomerKycControllerIntegrationTest` — full-stack test against real Postgres + Redis
  (Testcontainers), with `AuthServiceClient` stubbed via `@MockitoBean` (auth-service's own
  registration behavior is covered in its own test suite). Covers the complete
  register → verify → login (self-minted JWT, same signing secret) → submit KYC → start-review →
  approve flow, plus resource-ownership and RBAC denial checks, plus (Phase 10) the full live-HTTP
  maker-checker flow for customer status changes: a maker's request leaves the customer's status
  unchanged and returns `202`; a different checker's approval actually applies the requested
  status; self-approval is rejected with `403 SELF_APPROVAL_NOT_ALLOWED`.
- `ApprovalServiceTest` — unit tests (Mockito) for the maker-checker workflow, including
  `approve_byTheMakerThemselves_isRejected`.

The flagship real-Kafka proof for the outbox pattern (an `OutboxEvent` row genuinely reaching a
real broker via the scheduled publisher, then a producer-side DLQ on exhausted retries) lives in
[services/payment-service](../payment-service)'s `OutboxPublisherIntegrationTest` and
`OutboxPublisherTest` — the same `OutboxWriter`/`OutboxPublisher` code is duplicated here, so that
coverage isn't repeated per service.

Verified end-to-end against the live containerized stack, including the real synchronous call
into a running auth-service (not mocked) — see the Phase 3 completion report for the exact
`curl` sequence. Phase 8 additionally verified a real `customer.created` and three real
`kyc.updated` events (submit/start-review/approve) landing on the live Kafka broker's actual
topics, read back via `kafka-console-consumer`, with the corresponding `outbox_events` rows
confirmed `SENT` in Postgres. Phase 10 additionally verified the customer-status-change
maker-checker flow live: an `OPERATIONS` maker's `PATCH /customers/{id}/status` (requesting
`SUSPENDED`) returned `202 PENDING_APPROVAL` and left the customer `ACTIVE`; the maker's own
approval attempt was rejected `403 SELF_APPROVAL_NOT_ALLOWED`; a different `OPERATIONS` checker's
approval returned `200 APPROVED`; and the customer's status was then confirmed genuinely
`SUSPENDED`. Phase 13 additionally verified the support-ticket flow live: a customer's
`POST /support-requests` created an `OPEN` ticket only they (and staff) could read; `OPERATIONS`'s
`start-progress` then `resolve` transitioned it to `RESOLVED`; the resulting
`SUPPORT_REQUEST_CREATED`/`SUPPORT_REQUEST_RESOLVED` `audit.event`s were confirmed landed in a
real audit-service query by resource ID; and the `notification.requested` message genuinely
produced on resolution
was independently confirmed consumed by [notification-service](../notification-service) as a real
`SUPPORT_REQUEST_RESOLVED` notification row — see that service's README for the full transcript.
