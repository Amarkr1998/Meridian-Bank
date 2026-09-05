# account-service

**Status:** Implemented (Phase 4 — Account Opening + Account Management; Phase 5 — Beneficiary
Management; Phase 7 wired real balance display in; Phase 8 wired real Kafka event publishing in;
Phase 10 gated account blocking behind maker-checker approval; Phase 11 wired real audit-trail
publishing in).

Java 21, Spring Boot 3.5, Spring Security, Spring Data JPA, PostgreSQL (`account_service`
database), Redis, Kafka, Flyway, Resilience4j. See [pom.xml](pom.xml).

## Responsibility

Account opening workflow, account lifecycle, and beneficiary management — see
[docs/architecture/onboarding-flow.md](../../docs/architecture/onboarding-flow.md).

Account opening is never automatic: a request must be reviewed and approved by staff, and is
gated on the customer having a `KYC_VERIFIED` record. That check calls
`GET /api/v1/customers/{id}/kyc` on [customer-kyc-service](../customer-kyc-service), **forwarding
the calling customer's own bearer token** — the check on that side
(`#id == authentication.principal or hasAnyRole(...)`) passes because account opening is always
self-service, so the customer's own token authorizes the lookup made on their behalf. Wrapped in
a circuit breaker (see
[docs/adr/0014-circuit-breaker.md](../../docs/adr/0014-circuit-breaker.md)), never retried.

- Account opening request workflow: `ACCOUNT_REQUESTED` → `UNDER_REVIEW` → `APPROVED` (creates the
  account) / `REJECTED`
- Savings and current accounts, synthetic 10-digit account numbers (always masked in API
  responses — see [Governance Principles](../../docs/governance/governance-principles.md))
- Account lifecycle: `ACTIVE` ⇄ `FROZEN`, (`ACTIVE`|`FROZEN`) → `BLOCKED`, any non-`CLOSED` →
  `CLOSED` (terminal), each transition recorded in an append-only status history
- Configurable per-transaction and daily transaction limits (defaults from config, overridable
  per-account by staff) — see
  [Governance Principles](../../docs/governance/governance-principles.md) ("business thresholds
  are configurable data, not hardcoded constants")
- **Beneficiaries:** add (target account existence/ACTIVE check + self/duplicate guards) →
  `PENDING` → OTP-verified → `ACTIVE`; self-service `ACTIVE ⇄ INACTIVE`; staff-only
  `{PENDING|ACTIVE|INACTIVE} → BLOCKED → INACTIVE` (unblock); delete. `activatedAt` is recorded so
  `payment-service` (Phase 6) can later apply extra scrutiny to transfers toward a
  recently-activated beneficiary — see Section 16's "appropriate controls before high-value
  transfers." OTP verification reuses the same Redis-backed, demo-mode-exposed pattern as
  auth-service's MFA and customer-kyc-service's contact verification.
- Independently verifies JWTs issued by auth-service (shared HS256 secret), same as
  customer-kyc-service

## Real balance (Phase 7)

`GET /api/v1/accounts/{id}` now returns real `availableBalance`/`ledgerBalance` fields, sourced
live from [ledger-service](../ledger-service) — see `LedgerServiceClient` and
[ADR-0007](../../docs/adr/0007-double-entry-ledger.md). This is deliberately **not** a stored
column here: account-service still owns no balance state of its own, only configuration (type,
status, limits) — the ledger remains the single system of record for money.

Balance lookup fails *open*, not closed: if `ledger-service` is unreachable, the circuit breaker's
fallback degrades to `null` balance fields rather than failing the whole account lookup (see
`LedgerServiceClient`'s Javadoc) — a customer can still see their account exists and its status
even if the balance temporarily can't be fetched. This is the opposite policy from the KYC gate on
account opening, which fails *closed* (see above) — the two calls have different consequences for
getting it wrong. Only the single-account lookup is enriched; the list endpoint is not (would mean
N sequential calls to ledger-service per page).

Transaction *history* lives in [payment-service](../payment-service) (Phase 6) — see its README
for what a `SUCCESS` transaction there means now that real ledger posting is wired in (Phase 7).

## Kafka events (Phase 8)

Publishes `account.created` (on an account *opening request* being submitted — matching
[docs/kafka/topics.md](../../docs/kafka/topics.md)'s "Account opening request created", not
account activation) and `account.approved` (on approval, when the real `Account` is created) via
the transactional outbox pattern — see
[docs/adr/0005-outbox-pattern.md](../../docs/adr/0005-outbox-pattern.md). Same `OutboxWriter`/
`OutboxPublisher` implementation as [customer-kyc-service](../customer-kyc-service) and
[payment-service](../payment-service) (duplicated per service, not shared). As of Phase 13,
`account.approved` is genuinely consumed by [notification-service](../notification-service)
(`ACCOUNT_STATUS_CHANGED` notifications); `account.created` still has no consumer.

## Audit trail (Phase 11)

Publishes `audit.event` (same duplicated `audit` package pattern as every other producing service
— see [docs/adr/0012-audit-architecture.md](../../docs/adr/0012-audit-architecture.md)) for
`ACCOUNT_CREATED` (an opening request's approval — i.e. `account.approved`, not the initial
request), `ACCOUNT_FROZEN`, `ACCOUNT_BLOCKED` (fired from `AccountService.block`, which only ever
runs via the maker-checker approval below), `BENEFICIARY_ADDED`, and
`APPROVAL_CREATED`/`APPROVAL_COMPLETED`. Consumed and durably stored by
[audit-service](../audit-service). Unfreeze, close, limit changes, and beneficiary verify/
activate/deactivate/block/unblock are not individually audited yet — see audit-service's README
for the full cross-service action catalog.

## Maker-checker (Phase 10)

Blocking an account (`PATCH /accounts/{id}/block`) is gated behind a two-person approval — see
[docs/adr/0011-maker-checker.md](../../docs/adr/0011-maker-checker.md) and
[docs/architecture/maker-checker-flow.md](../../docs/architecture/maker-checker-flow.md). The
endpoint now returns `202 Accepted` with an `ApprovalRequestResponse` instead of executing
immediately: a maker (`OPERATIONS`/`ADMIN`) creates a `PENDING_APPROVAL` request, and a *different*
staff member must decide it via `PATCH /api/v1/approvals/{id}/approve` or `.../reject` before the
account is actually blocked. `ApprovalService.requirePending` enforces server-side — not just in
the UI — that the checker cannot be the same user as the maker (`403 SELF_APPROVAL_NOT_ALLOWED`),
and that a request can only be decided once (`409 INVALID_APPROVAL_TRANSITION`). Freeze, unfreeze,
close, and limit adjustments remain single-actor `OPERATIONS`/`ADMIN` actions — only blocking (the
most consequential, hardest-to-reverse-for-the-customer action) is gated. Only a single decision
round is implemented (no "return for revision" cycle), so one `approval_requests` row per request
is sufficient — see `ApprovalRequest`'s Javadoc.

## Explicitly out of scope

- **Beneficiary-specific transaction limits** aren't enforced yet — `payment-service` (Phase 6)
  checks per-transaction and daily limits, but nothing beneficiary-specific. `activatedAt` is
  captured here specifically so a future phase has what it needs for that.
- **Beneficiary name verification** — `beneficiaryName` is self-declared by the adding customer,
  not cross-checked against the recipient's real KYC identity. Doing that would require
  service-to-service auth this project doesn't have yet (the caller adding a beneficiary is
  neither the recipient nor staff, so the token-forwarding trick used for the KYC gate doesn't
  apply here). The account **number** is genuinely validated (must exist and be `ACTIVE`); the
  display name is not.
- Maker-checker on beneficiary status changes — beneficiary block/unblock remains a single
  `OPERATIONS`/`ADMIN`/`COMPLIANCE_OFFICER`/`RISK_ANALYST` action; only account blocking is gated
  (see "Maker-checker (Phase 10)" above).
- Joint/multi-holder accounts — see
  [docs/database/domain-model.md](../../docs/database/domain-model.md).

## API

All endpoints under `/api/v1`, all requiring authentication. Standard error envelope on failure.

### Accounts

| Method & Path | Auth | Description |
|---|---|---|
| `POST /accounts/requests` | self (`CUSTOMER`) | Request an account (requires verified KYC) |
| `GET /accounts/requests/mine` | self | Caller's own request history |
| `GET /accounts/requests/{id}` | owner or staff | Request detail |
| `GET /accounts/requests` | staff (`OPERATIONS`/`ADMIN`/`AUDITOR`) | Review queue |
| `POST /accounts/requests/{id}/start-review` | `OPERATIONS`/`ADMIN` | Claim for review |
| `POST /accounts/requests/{id}/approve` | `OPERATIONS`/`ADMIN` | Approve — creates the account |
| `POST /accounts/requests/{id}/reject` | `OPERATIONS`/`ADMIN` | Reject with reason |
| `GET /accounts/{id}` | owner or staff | Account detail (masked account number) |
| `GET /accounts` | any | Own accounts if `CUSTOMER`; filterable by `customerId`/`status` if staff |
| `GET /accounts/{id}/status-history` | owner or staff | Status change history |
| `PATCH /accounts/{id}/freeze` \| `/unfreeze` \| `/close` | `OPERATIONS`/`ADMIN` | Lifecycle actions |
| `PATCH /accounts/{id}/block` | `OPERATIONS`/`ADMIN` | **Maker-checker gated** — returns `202` with an approval request, does not block immediately |
| `PATCH /accounts/{id}/limits` | `OPERATIONS`/`ADMIN` | Adjust per-transaction/daily limits |

### Approvals (Phase 10)

| Method & Path | Auth | Description |
|---|---|---|
| `GET /approvals` | `OPERATIONS`/`ADMIN`/`AUDITOR` | Review queue, filterable by `status` |
| `GET /approvals/{id}` | `OPERATIONS`/`ADMIN`/`AUDITOR` | Request detail |
| `PATCH /approvals/{id}/approve` | `OPERATIONS`/`ADMIN`, and ≠ the requesting maker | Approve — actually blocks the account |
| `PATCH /approvals/{id}/reject` | `OPERATIONS`/`ADMIN`, and ≠ the requesting maker | Reject — account stays as it was |

### Beneficiaries

| Method & Path | Auth | Description |
|---|---|---|
| `POST /beneficiaries` | self | Add a beneficiary (`PENDING`, issues a verification OTP) |
| `POST /beneficiaries/{id}/verify` | owner | Confirm the OTP → `ACTIVE` |
| `POST /beneficiaries/{id}/resend-verification` | owner | Issue a fresh OTP |
| `GET /beneficiaries/{id}` | owner or staff | Beneficiary detail (masked account number) |
| `GET /beneficiaries` | any | Own list if `CUSTOMER`; filterable by `customerId`/`status` if staff |
| `GET /beneficiaries/{id}/status-history` | owner or staff | Status change history |
| `PATCH /beneficiaries/{id}/activate` \| `/deactivate` | owner | Self-service toggle (`ACTIVE ⇄ INACTIVE`) |
| `PATCH /beneficiaries/{id}/block` \| `/unblock` | `OPERATIONS`/`ADMIN`/`COMPLIANCE_OFFICER`/`RISK_ANALYST` | Fraud/compliance action |
| `DELETE /beneficiaries/{id}` | owner or `OPERATIONS`/`ADMIN` | Remove (hard delete — see note below) |

Deletion is a genuine removal, not a soft-delete, since the brief lists "Delete" as distinct from
"Deactivate" — a real production bank would more likely soft-delete/deactivate to preserve
transfer-history readability; noted here as a deliberate demo simplification.

## Cross-service integration

[payment-service](../payment-service) (Phase 6) calls `GET /api/v1/accounts/{id}` and
`GET /api/v1/beneficiaries/{id}` to validate a transfer's source account and beneficiary,
forwarding the paying customer's own bearer token — the same token-forwarding pattern used
throughout. `BeneficiaryResponse.destinationAccountId`/`destinationAccountStatus` (resolved from
the beneficiary's stored account number internally, since this service owns both) exist
specifically so payment-service never needs the raw account number.

## Running locally

From the repository root: `docker compose up -d --build account-service` (brings up `postgres`,
`redis`, `customer-kyc-service` — which in turn brings up `auth-service` — `ledger-service`, and
Kafka — waiting for the `kafka-init` topic bootstrap to complete — automatically). Listens on
`localhost:8083` (`ACCOUNT_SERVICE_PORT` in `.env`). Health: `GET /actuator/health` (deliberately
does not depend on Kafka reachability, same rationale as its ledger balance lookup).

## Tests

- `AccountServiceTest` — unit tests (Mockito) for the account status state machine.
- `AccountOpeningServiceTest` — unit tests for the opening-request workflow, including the
  KYC-verification gate and duplicate-in-progress-request guard, plus assertions that
  `account.created`/`account.approved` are published via `OutboxWriter` at the right steps.
- `BeneficiaryServiceTest` — unit tests for the beneficiary state machine and the add-time
  validations (nonexistent/inactive target account, self-beneficiary, duplicate).
- `AccountControllerIntegrationTest` — full-stack test against real Postgres (Testcontainers),
  with `CustomerKycServiceClient`/`LedgerServiceClient` stubbed via `@MockitoBean`. Covers submit →
  start-review → approve → account lifecycle with a real balance value asserted on the account
  lookup, a dedicated test proving account viewing survives `ledger-service` being unreachable
  (`accountViewing_survivesLedgerServiceBeingUnreachable`), plus resource-ownership and RBAC denial
  checks.
- `BeneficiaryControllerIntegrationTest` — full-stack test against real Postgres + Redis
  (Testcontainers). A test-only `TestAccountNumberLookup` bean reads the raw account number
  directly from the repository for fixture setup, since the public API never exposes it. Covers
  add → verify → deactivate → reactivate, staff block/unblock, and ownership checks.
- `ApprovalServiceTest` — unit tests (Mockito) for the maker-checker workflow, including
  `approve_byTheMakerThemselves_isRejected`.
- `AccountControllerIntegrationTest` additionally covers (Phase 10) the full live-HTTP
  maker-checker flow for account blocking: a maker's block request leaves the account `ACTIVE`
  and returns `202`; the same maker attempting to approve their own request is rejected with `403
  SELF_APPROVAL_NOT_ALLOWED`; a different checker's approval actually transitions the account to
  `BLOCKED`; and a rejected approval leaves the account `ACTIVE`.

Verified end-to-end against the live containerized eight-service stack (`postgres` + `redis` +
`kafka` + `auth-service` + `customer-kyc-service` + `account-service` + `payment-service` +
`ledger-service`): two real customers registered and opened accounts, one added the other as a
beneficiary (duplicate-add correctly rejected with `409`), verified it via OTP, toggled it
active/inactive, and a staff `RISK_ANALYST`/`ADMIN` token blocked and unblocked it — with the
customer's own attempt to block correctly rejected with `403` and the full transition history
visible via the status-history endpoint. Separately (Phase 7), after a real payment moved money
between two such accounts, `GET /api/v1/accounts/{id}` was confirmed to return the real,
post-transfer `availableBalance`/`ledgerBalance` sourced live from `ledger-service` — see
[services/ledger-service/README.md](../ledger-service/README.md) for the full transcript. Phase 8
additionally verified a real `account.created` and a real `account.approved` event landing on the
live Kafka broker's actual topics (read back via `kafka-console-consumer`), with the corresponding
`outbox_events` rows confirmed `SENT` in Postgres. Phase 10 additionally verified the account-block
maker-checker flow live: an `OPERATIONS` maker's `PATCH /accounts/{id}/block` returned `202
PENDING_APPROVAL` and left the account `ACTIVE`; the maker's own approval attempt was rejected
`403 SELF_APPROVAL_NOT_ALLOWED`; a different `OPERATIONS` checker's approval returned `200
APPROVED`; and the account was then confirmed genuinely `BLOCKED`.
