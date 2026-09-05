# ledger-service

**Status:** Implemented (Phase 7 — Double-Entry Ledger + Concurrency; Phase 12 added
reconciliation + the outbox pattern).

Java 21, Spring Boot 3.5, Spring Security, Spring Data JPA, PostgreSQL (`ledger_service`
database), Kafka, Flyway. See [pom.xml](pom.xml). No Redis, no Resilience4j — this is a leaf
service; it makes no outbound calls to any other Meridian service.

## Responsibility

The single system of record for money. Real double-entry postings and materialized account
balances, backing [account-service](../account-service)'s balance display and
[payment-service](../payment-service)'s real money movement. See
[ADR-0007](../../docs/adr/0007-double-entry-ledger.md) and
[ADR-0008](../../docs/adr/0008-concurrency-strategy.md).

**The invariant this service exists to enforce:** a balance is never assigned, only derived from
the sum of its ledger entries. There is no `UPDATE balances SET available_balance = available_balance - :amount`
anywhere in this codebase, and there never will be — see `LedgerPostingService`.

## Trust boundary

For the core posting/balance/entries API, `ledger-service` performs **no ownership or
business-authorization checks of its own** — it trusts the caller (currently only
`payment-service`) to have already validated that a posting should happen. `SecurityConfig`'s base
chain only verifies the caller holds a valid JWT (`.anyRequest().authenticated()`); this is
deliberate — by the time a posting request reaches here, `payment-service` has already run the
full validation pipeline documented in its own README, forwarding the customer's bearer token
throughout. Re-deriving ownership here would duplicate logic without adding real protection, since
only trusted internal services are expected to call this API directly. This is explicitly *not*
the final security boundary for money movement — `payment-service` is. The reconciliation API
added in Phase 12 is the one exception: it's a staff-facing review queue, not a service-to-service
call, so it layers `@PreAuthorize` role checks on top of the same base chain — see
`ReconciliationController`.

## Data model

- `ledger_entries` — append-only. Each posting writes exactly one `DEBIT` and one `CREDIT` row,
  sharing a `transaction_id`, both `amount > 0`. A unique index on
  `(transaction_id, account_id, entry_type)` makes re-posting the same `transaction_id` a no-op
  (idempotent replay — see below) rather than a duplicate.
- `balances` — one row per account, materialized (`available_balance`, `ledger_balance` — kept
  equal; no separate "pending/hold" concept exists yet). Lazily created on first reference (either
  the first posting or the first balance lookup) via an atomic
  `INSERT ... ON CONFLICT DO NOTHING`, starting at zero. There is no deposit/funding endpoint in
  the system yet — the only way money enters is a posting between two existing accounts, so every
  account genuinely starts at zero (see Known limitation below).

## Concurrency strategy (the core deliverable of this phase)

Every posting acquires `SELECT ... FOR UPDATE` row locks (`BalanceRepository.findByIdForUpdate`,
`LockModeType.PESSIMISTIC_WRITE`) on both the debit and credit account's balance row before
mutating either.

**Deadlock avoidance:** the two accounts are always locked in a fixed order — by `UUID.compareTo()`
— regardless of which one is the debit side and which is the credit side. Without this, two
concurrent transfers in opposite directions between the same two accounts (A→B and B→A) could each
hold one lock while waiting for the other, deadlocking. `LedgerPostingService.post` computes the
lock order once and always acquires the numerically-smaller UUID's lock first.

**Overdraft prevention:** the sufficiency check (`hasSufficientAvailableBalance`) happens only
*after* the debit account's row lock is held, so no two concurrent debits can both observe a
"sufficient" balance and both proceed — one always sees the other's committed decrement.

**Idempotent replay:** if `post` is called again with a `transaction_id` that already has entries,
it returns the existing result instead of posting again (`replay`) — this is what lets
`payment-service` safely retry a posting call after a network timeout without double-moving money.

This is proven, not just asserted — see Tests.

## Known limitation: no funding source

There is no API to create money — deliberately, since a real double-entry ledger cannot originate
funds from nothing (see ADR-0007). Every account starts at a real zero balance, and the only way
balances change is a `POST /postings` between two existing accounts. This means, as it stands
today, no transfer can ever succeed anywhere in the system, since no account can ever have a
positive balance through the API alone. This is an intentional, honestly-documented gap: a real
bank has external funding rails (an internal "treasury"/genesis account, ACH inbound, teller cash
deposit) that are out of scope for every phase planned so far. The live end-to-end verification
below seeds an opening balance directly in Postgres to demonstrate real posting — exactly what the
concurrency tests do — since that is the only way to observe a successful transfer today.

## Reconciliation + the outbox pattern (Phase 12)

This service's first use of Kafka — see docs/reconciliation/reconciliation-design.md and
[docs/adr/0005-outbox-pattern.md](../../docs/adr/0005-outbox-pattern.md). No new microservice was
added — reconciliation lives inside `ledger-service` itself (CLAUDE.md's fixed 9-service list has
no separate reconciliation service; `ledger_entries` is the "internal source of truth" reconciliation
compares against, so it belongs where that data already lives).

**A scheduled job (`ReconciliationJob`, default every 60s — `meridian.reconciliation.poll-interval-ms`)
compares every real ledger transaction against a synthetic external counterpart** and classifies it
`MATCHED` / `MISMATCHED` / `PENDING` — never mutating `ledger_entries`, only writing
`reconciliation_records`. All real logic lives in `ReconciliationService.run()`, callable directly
in tests without waiting on the scheduler.

- **The external feed is always synthetic** (`ExternalFeedGenerator`) — never real external bank
  data or a live third-party integration. It is not randomness dressed up as realism: the outcome
  for a given `transactionId` is a deterministic, reproducible function of that id's own UUID bits
  (see its Javadoc for the exact bands): **70% immediate exact match**, **20% delayed-but-exact
  match** (not "available" to the reconciliation job until
  `meridian.reconciliation.external-feed-delay-seconds`, default 120s, after generation — a genuine
  `PENDING` → `MATCHED` transition driven by wall-clock time passing, not a second random roll),
  **10% immediate but deliberately wrong amount** (a small, non-zero, deterministic discrepancy) —
  demonstrating the actual control this feature exists to prove, not just the happy path.
- **Idempotent and safe to re-run.** `external_transactions.reference` is unique per
  `transactionId`, so a transaction's synthetic counterpart is generated exactly once, ever. Once a
  `reconciliation_records` row leaves `PENDING` (i.e. reaches `MATCHED`, `MISMATCHED`,
  `INVESTIGATION`, or `RESOLVED`), the job never touches it again — only `PENDING` records are
  re-evaluated each run.
- **Staff workflow:** a `MISMATCHED` record can be claimed (`MISMATCHED` → `INVESTIGATION`) and then
  resolved with notes (`INVESTIGATION` → `RESOLVED`) — see API below. Both a mismatch being
  detected and a resolution both publish `audit.event` (`RECONCILIATION_MISMATCH_DETECTED`,
  `RECONCILIATION_RESOLVED`) to the real, running [audit-service](../audit-service) — this
  service's first use of the Phase 11 audit pattern.
- **`reconciliation.completed`** is published once per run via the same `OutboxWriter`/
  `OutboxPublisher` implementation duplicated (not shared) across every other outbox-using service
  — the flagship real-Kafka proof for that pattern lives in
  [payment-service](../payment-service)'s `OutboxPublisherIntegrationTest`, not repeated here.

## API

All endpoints under `/api/v1/ledger`, all requiring authentication only (see Trust boundary).

| Method & Path | Description |
|---|---|
| `POST /postings` | Post a balanced debit/credit pair for a transaction. Idempotent by `transactionId`. `409 INSUFFICIENT_BALANCE` if the debit account can't cover it. |
| `GET /accounts/{id}/balance` | Current available/ledger balance (lazily zero-initialized) |
| `GET /accounts/{id}/entries` | Paged ledger entry history for an account |
| `GET /reconciliation-records` | Staff (`OPERATIONS`/`ADMIN`/`AUDITOR`/`COMPLIANCE_OFFICER`) — review queue, filterable by `status` |
| `GET /reconciliation-records/{id}` | Staff (same roles) — record detail |
| `PATCH /reconciliation-records/{id}/start-investigation` | `OPERATIONS`/`ADMIN` — `MISMATCHED` → `INVESTIGATION` |
| `PATCH /reconciliation-records/{id}/resolve` | `OPERATIONS`/`ADMIN` — `INVESTIGATION` → `RESOLVED`, with notes |

## Running locally

From the repository root: `docker compose up -d --build ledger-service` (brings up `postgres` and
Kafka — waiting for the `kafka-init` topic bootstrap to complete — automatically). Listens on
`localhost:8085` (`LEDGER_SERVICE_PORT` in `.env`). Health: `GET /actuator/health` (deliberately
does not depend on Kafka reachability — see `OutboxPublisher`'s Javadoc).

## Tests

- `LedgerPostingServiceTest` — unit tests (Mockito): balanced entries written, balances updated
  correctly, self-transfer rejected, insufficient balance rejected without mutating anything,
  idempotent replay of an existing `transactionId`, and fixed lock ordering regardless of which
  account is debited vs. credited (`post_locksAccountsInAFixedOrder_regardlessOfDebitCreditDirection`).
- `LedgerPostingConcurrencyTest` — **the proof**, against real Postgres (Testcontainers), not
  mocks:
  - `concurrentDebitsFromTheSameAccount_neverOverdraw` — 5 threads simultaneously attempt to debit
    $30 each from a $100 balance; asserts exactly 3 succeed, 2 correctly fail with insufficient
    balance, and the final balance ($10) never goes negative.
  - `concurrentOppositeDirectionTransfersBetweenSameTwoAccounts_doNotDeadlock` — 20 rounds of A→B
    and B→A running concurrently on two threads between the same two accounts; asserts both
    threads complete within a bounded time (would hang indefinitely under naive per-call lock
    ordering) and balances net back to their starting point.
- `LedgerControllerIntegrationTest` — HTTP-layer tests against real Postgres: successful posting,
  idempotent replay via the API, insufficient-balance `409`, balance and entry-history retrieval,
  and authentication requirement.
- `ExternalFeedGeneratorTest` — unit tests pinning down the deterministic 70/20/10 classification
  bands concretely: bucket 0 is an immediate exact match, bucket 7 is a delayed-but-exact match
  (not available until the configured delay passes), bucket 9 is an immediate wrong-amount
  mismatch, a call is idempotent for an already-generated transaction, and classification is a
  pure function of the transaction id (same id, same outcome, called twice).
- `ReconciliationServiceTest` — unit tests (Mockito) for the comparison engine and the staff
  workflow in isolation: exact match → `MATCHED`; wrong external amount → `MISMATCHED` +
  `RECONCILIATION_MISMATCH_DETECTED` audited; not-yet-available external record → `PENDING`; a
  record already `MATCHED` is never reprocessed (no interaction with the generator at all);
  `startInvestigation` only accepts a `MISMATCHED` record; `resolve` only accepts one
  `INVESTIGATION` and audits `RECONCILIATION_RESOLVED`.
- `ReconciliationIntegrationTest` — real Postgres, real `LedgerPostingService` postings (not
  mocks): three genuine transactions crafted to land in each classification band are posted, and a
  real `ReconciliationService.run()` correctly classifies all three in one pass; the `PENDING`
  record is proven to genuinely transition to `MATCHED` once wall-clock time passes the (test-
  shortened, 2s) external-feed delay and the job runs again — the actual mechanism, not a mocked
  clock; a re-run is proven idempotent (no duplicate `reconciliation_records` row).
- `ReconciliationControllerIntegrationTest` — full-stack RBAC + workflow test against real
  Postgres: staff can list/filter by status and read a record; a `CUSTOMER` token is rejected
  `403`; an `AUDITOR` can view but is correctly rejected `403` attempting to start an
  investigation; the full `OPERATIONS` staff `start-investigation` → `resolve` flow succeeds end to
  end with notes persisted; an unknown record id returns `404`.

Verified end-to-end against the live six-service stack (`postgres` + `redis` + `kafka` +
`auth-service` + `customer-kyc-service` + `account-service` + `payment-service` +
`ledger-service`, none mocked): two real customers registered, verified KYC, and opened accounts;
one account's opening balance was seeded directly in Postgres (see Known limitation above); a real
payment moved real money — the source account's balance dropped from $500.00 to $400.00 and the
destination account's rose from $0.00 to $100.00, both confirmed via `GET /api/v1/accounts/{id}`
on `account-service`, which itself confirmed reading a real balance from this service rather than a
placeholder; retrying the same `Idempotency-Key` returned the identical transaction without a
second posting (confirmed via `GET /api/v1/ledger/accounts/{id}/entries` showing exactly one
`DEBIT` entry, not two); a payment within the per-transaction limit but exceeding the real
available balance ($450.00 against $400.00) came back as `201` with `status: "FAILED"` and
`failureCode: "INSUFFICIENT_BALANCE"`, with the balance provably unchanged afterward.

Phase 12 additionally verified the reconciliation feature live against the running ten-service
stack (`ledger-service` calls made directly by a staff token, which its trust boundary already
permits): three real postings with transaction ids deliberately crafted to land in each
`ExternalFeedGenerator` band were posted, and the real scheduled `ReconciliationJob` (not called
directly) classified all three correctly on its very next tick — an exact-amount transaction
`MATCHED`, a deliberately-wrong-amount transaction `MISMATCHED` (internal $75.00 vs. a real
generated external $82.41), and a delayed-band transaction `PENDING`. The `MISMATCHED` record
genuinely published `RECONCILIATION_MISMATCH_DETECTED` to the real `audit-service`; an `OPERATIONS`
staff token then claimed it (`MISMATCHED` → `INVESTIGATION`) while an `AUDITOR` token was correctly
rejected `403` attempting the same, and resolved it with notes (`INVESTIGATION` → `RESOLVED`),
genuinely publishing `RECONCILIATION_RESOLVED` to `audit-service` with the resolving staff member's
id and notes intact. The `PENDING` record was independently confirmed to genuinely transition to
`MATCHED` exactly 120 seconds after creation (the real, unmodified
`external-feed-delay-seconds` default) on the job's next scheduled tick — proving the
wall-clock-driven transition is real, not simulated. `reconciliation.completed` was confirmed
landing on the real Kafka topic via `kafka-console-consumer`.
