# payment-service

**Status:** Implemented (Phase 6 — Payment Processing + Idempotency; Phase 7 wired real ledger
posting in; Phase 8 wired real Kafka event publishing in; Phase 9 wired real fraud risk checking in;
Phase 10 added a maker-checker-gated release path for REVIEW-held payments; Phase 11 wired real
audit-trail publishing in).

Java 21, Spring Boot 3.5, Spring Security, Spring Data JPA, PostgreSQL (`payment_service`
database), Redis, Kafka, Flyway, Resilience4j. See [pom.xml](pom.xml).

## Responsibility

Account-to-account transfers, orchestrated across [account-service](../account-service) (source
account + beneficiary validation), [customer-kyc-service](../customer-kyc-service) (KYC status),
and [ledger-service](../ledger-service) (real double-entry posting), with mandatory idempotency.
See [docs/architecture/payment-flow.md](../../docs/architecture/payment-flow.md).

## What this phase honestly does and does not do

This is the most consequential scope boundary in the project so far, so it's stated plainly rather
than left implicit:

- **Real money now genuinely moves (Phase 7).** The `PROCESSING` step calls
  [ledger-service](../ledger-service) to post a balanced debit/credit pair — see
  `PaymentService.process` and [ADR-0007](../../docs/adr/0007-double-entry-ledger.md). A `SUCCESS`
  result means the source account's real balance decreased and the destination account's real
  balance increased, both durably recorded as ledger entries. If the source account can't cover
  the amount, the ledger rejects the posting and the transaction fails with
  `failureCode: "INSUFFICIENT_BALANCE"` — a genuine business outcome, not a placeholder.
- **Real fraud risk checking now happens (Phase 9).** The `RISK_CHECK` step calls
  [fraud-risk-service](../fraud-risk-service) for a real ALLOW/REVIEW/BLOCK decision — see
  `PaymentService.process` and [docs/architecture/fraud-flow.md](../../docs/architecture/fraud-flow.md).
  A `BLOCK` fails with `failureCode: "FRAUD_BLOCKED"` and there is no way to release it — a BLOCK is
  a terminal decision in this project. A `REVIEW` also still fails, with
  `failureCode: "FRAUD_REVIEW_REQUIRED"` — that itself hasn't changed — but as of Phase 10 a member
  of staff can request its release, and, on a *different* staff member's approval, a genuinely new
  transaction is created and processed. See "Maker-checker (Phase 10)" below for exactly what this
  does and doesn't do; it is deliberately not a true hold/resume of the original transaction.
- **Refund and reversal are not implemented.** Per the documented design (payment-flow.md), a
  reversal posts an inverse ledger entry — the ledger to reverse against now exists (Phase 7), but
  the reversal flow itself is still future work.
- **What genuinely IS real:** the full validation pipeline (source account ownership/status,
  beneficiary ownership/status — enforcing the Phase 5 "verified beneficiary" control, KYC
  verification, currency match, per-transaction and daily limits), real fraud risk checking, real
  ledger posting, and, above all, **idempotency** — this is fully implemented and tested, including
  a genuine concurrent-request race against real Redis (see Tests below). A `FAILED` transaction
  (bad account, unverified beneficiary, limit exceeded, insufficient balance, fraud
  blocked/flagged, ...) is a complete, correctly-handled result — it's returned as `201 Created`
  with `status: "FAILED"` and a `failureCode`, not as an HTTP error; see PaymentController.

## Audit trail (Phase 11)

Publishes `audit.event` (same duplicated `audit` package pattern as every other producing service
— see [docs/adr/0012-audit-architecture.md](../../docs/adr/0012-audit-architecture.md)) for
`PAYMENT_INITIATED`, `PAYMENT_COMPLETED`, and `PAYMENT_FAILED` — mirroring this service's own
`payment.initiated`/`payment.completed`/`payment.failed` domain events almost 1:1, at the same
call sites — plus `APPROVAL_CREATED`/`APPROVAL_COMPLETED` for the release flow above. Consumed and
durably stored by [audit-service](../audit-service). The actor recorded for all of these is the
paying customer (`actorRole: "CUSTOMER"`), including for a staff-approved release — the distinct
`APPROVAL_COMPLETED` event already attributes that decision to the checker who made it.

## Maker-checker (Phase 10)

A member of staff (`OPERATIONS`/`COMPLIANCE_OFFICER`/`RISK_ANALYST`/`ADMIN`) can request the release
of a transaction that `FAILED` with `FRAUD_REVIEW_REQUIRED` via
`POST /payments/{id}/request-release` — see
[docs/adr/0011-maker-checker.md](../../docs/adr/0011-maker-checker.md) and
[docs/architecture/maker-checker-flow.md](../../docs/architecture/maker-checker-flow.md). This
returns `202 Accepted` with a `PENDING_APPROVAL` `ApprovalRequestResponse`; nothing happens yet. A
*different* staff member must approve it via `PATCH /api/v1/approvals/{id}/approve`
(`ApprovalService.requirePending` enforces server-side that the checker cannot be the maker —
`403 SELF_APPROVAL_NOT_ALLOWED`).

Only on approval does `PaymentService.releaseHeldPayment` run: it reads the original transaction's
already-validated source account, beneficiary, amount, and currency, and creates a **brand-new**
transaction from them — skipping `RISK_CHECK` entirely (staff have already exercised judgment to
override the flag) but running the same ledger-posting step as any other payment, so it can still
genuinely fail with `INSUFFICIENT_BALANCE` if the balance has since changed. The original
transaction is never mutated — it stays `FAILED`/`FRAUD_REVIEW_REQUIRED` forever, and
`ApprovalRequest.resultTransactionId` records the id of the new transaction the release created.
This is a deliberate design choice, not an oversight: reopening the *original* transaction would
mean resurrecting an idempotency claim whose replay window may have long since closed, which is a
harder and less honest thing to model than "staff approved a fresh attempt." A rejected approval
leaves the original transaction exactly as it was and creates nothing.

## Kafka events + the outbox pattern (Phase 8)

Publishes `payment.initiated` (once the transaction row exists, before validation runs),
`payment.completed` (on `SUCCESS`), and `payment.failed` (on any `FAILED` outcome, from whichever
validation step rejected it) via the transactional outbox pattern — see
[docs/adr/0005-outbox-pattern.md](../../docs/adr/0005-outbox-pattern.md) and
[docs/architecture/kafka-architecture.md](../../docs/architecture/kafka-architecture.md).
`payment.reversed` is not published — there is no reversal flow yet (see above).

`OutboxWriter` appends a row to this service's own `outbox_events` table in the same database
transaction as the business change; `OutboxPublisher` polls PENDING rows on a fixed schedule and
relays them to the real Kafka broker, retrying failed sends with exponential backoff. A row that
exhausts `meridian.outbox.max-attempts` (default 5) is marked terminally FAILED and best-effort
published to a `<eventType>.DLQ` topic instead — a **producer-side** retry/DLQ (`fraud-risk-service`
is reached synchronously, not via Kafka consumption — see its README). `audit-service` (Phase 11)
consumes a different topic entirely (`audit.event`, which this service also now publishes to for
its sensitive actions — see "Audit trail (Phase 11)" below). As of Phase 13,
[notification-service](../notification-service) genuinely consumes `payment.completed`
(`PAYMENT_SUCCESS` notifications) and `payment.failed` (`PAYMENT_FAILED` notifications);
`payment.initiated` still has no consumer. See
`OutboxPublisher`'s Javadoc for the full policy. The same `OutboxWriter`/`OutboxPublisher`
implementation is duplicated (not shared) in
[customer-kyc-service](../customer-kyc-service) and [account-service](../account-service).

## Idempotency (docs/adr/0006-idempotency.md)

Every `POST /api/v1/payments` requires an `Idempotency-Key` header. The key is claimed atomically
in Redis (`SET NX`, keyed by customer + key) *before* any validation runs:

- **Same key, same request** → the original result is returned; nothing is reprocessed.
- **Same key, different request payload** → `409 IDEMPOTENCY_KEY_REUSE` (a client bug, not a retry).
- **Same key, still mid-flight** → `409 PAYMENT_IN_PROGRESS` rather than double-processing.
- The `(customer_id, idempotency_key)` unique constraint in Postgres is the durable fallback
  behind the Redis claim — see `PaymentService.createPayment`'s javadoc for why a violation there
  is treated as a rare-edge-case failure rather than recovered within the same transaction
  (Postgres aborts the whole transaction on a constraint violation, which would make in-transaction
  recovery unreliable).

## Validation order

1. Idempotency claim (short-circuits everything below on replay)
2. Source account: exists, owned by the caller, `ACTIVE` (`account-service`, forwarded token)
3. Beneficiary: exists, owned by the caller, `ACTIVE` (`account-service`, forwarded token) — a
   `PENDING`/unverified beneficiary cannot receive a payment
4. Destination account (resolved from the beneficiary): `ACTIVE`
5. Currency matches the source account's currency
6. Customer has a `KYC_VERIFIED` record (`customer-kyc-service`, forwarded token)
7. Amount ≤ source account's per-transaction limit
8. (Amount + today's successful transfers from this account) ≤ daily limit — **not yet
   concurrency-hardened**; see below
9. Risk check (`fraud-risk-service`, forwarded token) → `FAILED`/`FRAUD_BLOCKED` on BLOCK,
   `FAILED`/`FRAUD_REVIEW_REQUIRED` on REVIEW
10. Processing → real ledger posting (`ledger-service`) → `SUCCESS`, or `FAILED` with
    `INSUFFICIENT_BALANCE` if the ledger rejects the posting

Every synchronous call forwards the caller's own bearer token — the downstream endpoints already
enforce resource ownership, so a successful call *is* the ownership proof; payment-service never
needs its own copy of "does this account belong to this customer." See
[docs/adr/0014-circuit-breaker.md](../../docs/adr/0014-circuit-breaker.md) for the circuit breaker
wrapping these calls (never retried).

## Known simplification: daily-limit concurrency

The daily-limit check (step 8) is a plain `SUM(...)` query in `payment-service`'s own database, not
protected by row locking. Two concurrent transfers from the same account could theoretically both
read the same "used today" total and both pass. This is a deliberate, documented gap, distinct from
`ledger-service`'s own concurrency guarantees (Phase 7,
[ADR-0008](../../docs/adr/0008-concurrency-strategy.md)): the ledger's row-level locking makes
overdraft against the *real balance* impossible even under concurrency (see its README), but a
daily-limit race here could still let two concurrent transfers each individually clear their
per-transaction limit and the ledger's balance check while jointly exceeding the account's daily
cap. The **idempotency** claim itself (this phase's actual concurrency-correctness deliverable)
*is* fully atomic — see Tests.

## API

All endpoints under `/api/v1/payments`, all requiring authentication.

| Method & Path | Auth | Description |
|---|---|---|
| `POST /payments` | self, header `Idempotency-Key` required | Create a transfer |
| `GET /payments/{id}` | owner or staff | Transaction detail |
| `GET /payments` | any | Own transactions if `CUSTOMER`; filterable by `customerId`/`status` if staff |
| `GET /payments/{id}/status-history` | owner or staff | Full lifecycle trace |
| `POST /payments/{id}/request-release` | `OPERATIONS`/`COMPLIANCE_OFFICER`/`RISK_ANALYST`/`ADMIN` | **Maker-checker gated** — requests release of a `FRAUD_REVIEW_REQUIRED` transaction, returns `202` |
| `GET /approvals` | same staff roles | Review queue, filterable by `status` |
| `GET /approvals/{id}` | same staff roles | Request detail |
| `PATCH /approvals/{id}/approve` | same staff roles, and ≠ the requesting maker | Approve — creates and processes a new transaction |
| `PATCH /approvals/{id}/reject` | same staff roles, and ≠ the requesting maker | Reject — original transaction untouched |

## Running locally

From the repository root: `docker compose up -d --build payment-service` (brings up `postgres`,
`redis`, `account-service` — which cascades to `customer-kyc-service` and `auth-service` —
`ledger-service`, `fraud-risk-service`, and Kafka — waiting for the `kafka-init` topic bootstrap to
complete — automatically). Listens on `localhost:8084` (`PAYMENT_SERVICE_PORT` in `.env`). Health:
`GET /actuator/health` (deliberately does not depend on Kafka reachability — see
`OutboxPublisher`'s Javadoc).

## Tests

- `PaymentServiceTest` — unit tests (Mockito) for every step of the validation pipeline
  (invalid/inactive source account, unverified beneficiary, inactive destination, currency
  mismatch, KYC not verified, per-transaction and daily limit breaches, ledger-reported
  insufficient balance, fraud-risk-reported BLOCK/REVIEW, and the success path — including a
  `verify(ledgerServiceClient).post(...)` assertion), plus idempotent-replay short-circuiting, plus
  assertions that `payment.initiated`/`payment.completed`/`payment.failed` are published via
  `OutboxWriter` at the right steps.
- `PaymentControllerIntegrationTest` — full-stack test against real Postgres + Redis
  (Testcontainers), with `AccountServiceClient`/`CustomerKycServiceClient`/`LedgerServiceClient`/
  `FraudRiskServiceClient` stubbed via `@MockitoBean`. Covers: full success + sequential replay
  (asserting the ledger client was actually called), key-reuse-with-different-payload rejection,
  missing-header rejection, a `FAILED` result returned as `201` (not an HTTP error) for validation
  failures, a ledger-reported insufficient balance, and a fraud-risk-reported BLOCK, RBAC/ownership,
  and — the important one — **5 threads firing the identical request with the identical idempotency
  key simultaneously against real Redis**, asserting every successful response references the same
  transaction id.
- `OutboxPublisherTest` — unit tests (Mockito, no real broker) proving the retry/backoff/DLQ policy
  precisely: success marks a row SENT; a failure below `max-attempts` schedules a retry with the
  correct exponential backoff; exhausting `max-attempts` marks the row FAILED and publishes the
  same envelope to `<eventType>.DLQ`; and a DLQ publish that itself fails still leaves the row
  terminally FAILED rather than retrying forever.
- `ApprovalServiceTest` — unit tests (Mockito) for the maker-checker workflow:
  `approve_byTheMakerThemselves_isRejected` (asserting `paymentService` is never invoked), that a
  different checker's approval calls `PaymentService.releaseHeldPayment` with the checker's own
  bearer token and records the returned transaction id as `resultTransactionId`, and that reject
  never calls `releaseHeldPayment` at all.
- `PaymentControllerIntegrationTest` additionally covers (Phase 10) the full live-HTTP
  maker-checker release flow: a REVIEW-flagged payment fails with `FRAUD_REVIEW_REQUIRED`; a
  maker's release request returns `202 PENDING_APPROVAL`; the maker's own approval attempt is
  rejected with `403 SELF_APPROVAL_NOT_ALLOWED`; a different checker's approval returns `200
  APPROVED` with a `resultTransactionId` distinct from the original, and that new transaction is
  independently confirmed `SUCCESS` while the original is confirmed still `FAILED`/
  `FRAUD_REVIEW_REQUIRED`; and, separately, a rejected approval leaves the original untouched and
  creates no new transaction.
- `OutboxPublisherIntegrationTest` — **the flagship proof for Phase 8**, against a real Postgres
  *and* a real Kafka broker (Testcontainers, `org.testcontainers:kafka`, not mocked): a business
  method calls `OutboxWriter.write(...)`, the row lands in Postgres as PENDING, and — without ever
  calling `OutboxPublisher` directly — the test simply waits for its normal `@Scheduled` poll loop
  to run, then asserts a real `KafkaConsumer` subscribed to the real topic receives the message
  (correct key, correct envelope shape, correct payload) and that the `outbox_events` row is then
  `SENT` in the database. This proves the actual scheduled wiring works end to end, not just the
  publish logic in isolation.

Verified end-to-end against the live nine-service stack (`postgres` + `redis` + `kafka` +
`auth-service` + `customer-kyc-service` + `account-service` + `payment-service` +
`ledger-service` + `fraud-risk-service`, none mocked): two real customers registered, verified
KYC, and opened accounts; one added and OTP-verified the other as a beneficiary; a real transfer
succeeded with full cross-service validation and moved a real balance (confirmed via
`account-service`); retrying with the same `Idempotency-Key` returned the identical transaction
without a second ledger posting; reusing the key with a different amount was rejected with `409`;
a transfer over the per-transaction limit came back as `201` with `status: "FAILED"`; a transfer
within the limit but exceeding the real available balance came back as `201` with
`status: "FAILED"` and `failureCode: "INSUFFICIENT_BALANCE"`; and a second customer's transaction
list correctly showed none of the first customer's activity. Phase 8 additionally verified real
`payment.initiated` and `payment.completed` events landing on the live Kafka broker's actual
topics (read back via `kafka-console-consumer`), with the corresponding `outbox_events` rows
confirmed `SENT` in Postgres — see [docs/kafka/topics.md](../../docs/kafka/topics.md) for the
envelope shape. Phase 9 additionally verified a real cascading fraud sequence against a live
`fraud-risk-service`: escalating transfers to the same beneficiary correctly moved from `REVIEW`
(score 40, `FRAUD_REVIEW_REQUIRED`) to `BLOCK` (score 100, `FRAUD_BLOCKED`) as real risk history
accumulated, with the account's real balance untouched by every rejected payment — see
[fraud-risk-service/README.md](../fraud-risk-service/README.md) for the full transcript. Phase 10
additionally verified the release flow live: a $9,000 transfer to a new beneficiary scored 65
(`HIGH_AMOUNT` + `NEW_BENEFICIARY_HIGH_AMOUNT`) and failed `FRAUD_REVIEW_REQUIRED`; an `OPERATIONS`
maker's `request-release` call returned `202 PENDING_APPROVAL` and their own approval attempt was
rejected `403 SELF_APPROVAL_NOT_ALLOWED`; a different `RISK_ANALYST` checker's approval returned
`200 APPROVED` with a `resultTransactionId` distinct from the original; that new transaction was
independently confirmed `SUCCESS`, the original stayed `FAILED`/`FRAUD_REVIEW_REQUIRED`
untouched, and the source account's real balance (seeded directly in `ledger-service`'s Postgres
per the "no funding source" limitation above) genuinely dropped from $50,000.00 to $41,000.00 —
confirmed via `GET /api/v1/accounts/{id}` on `account-service`, which itself sources the figure
live from `ledger-service`.
