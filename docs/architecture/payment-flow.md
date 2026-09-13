# Payment Flow

End-to-end account-to-account transfer, including validation order, idempotency, fraud/risk check,
ledger posting, and event propagation.

> **Implementation status (Phase 20):** `payment-service` implements the full validation pipeline
> (source account, beneficiary, KYC, currency, per-transaction/daily limits) and idempotency —
> including a genuine concurrent-request race proven against real Redis — exactly as below. The
> `RISK_CHECK` step now calls a real `fraud-risk-service` (see
> [services/fraud-risk-service/README.md](../../services/fraud-risk-service/README.md)) and
> genuinely enforces its decision: `BLOCK` fails with `failureCode: "FRAUD_BLOCKED"`, `REVIEW`
> also fails, with `failureCode: "FRAUD_REVIEW_REQUIRED"`. A different staff member can approve a
> maker-checker release that creates a new transaction; the original is not resumed. The
> `PROCESSING` step
> posts a real balanced debit/credit pair to `ledger-service` (pessimistic-locking,
> fixed-lock-ordering concurrency control, proven against real Postgres under concurrent load —
> see [services/ledger-service/README.md](../../services/ledger-service/README.md)), so a
> `SUCCESS` result means real money genuinely moved; an insufficient real balance produces a
> `FAILED` result with `failureCode: "INSUFFICIENT_BALANCE"`. Kafka events (Phase 8) are real for
> `payment.initiated`/`payment.completed`/`payment.failed` — see
> [docs/kafka/topics.md](../kafka/topics.md). Audit and notification consumers shown below are
> implemented. See
> [services/payment-service/README.md](../../services/payment-service/README.md).

## Payment Lifecycle

```text
INITIATED → VALIDATING → RISK_CHECK → PROCESSING → SUCCESS
                                                   ↘ FAILED
                                     SUCCESS → REVERSED (separate reversal flow)
```

## Sequence Diagram — Successful Transfer

```mermaid
sequenceDiagram
    actor C as Customer
    participant WEB as Customer Portal
    participant GW as API Gateway
    participant PAY as payment-service
    participant REDIS as Redis (idempotency)
    participant ACC as account-service
    participant KYC as customer-kyc-service
    participant FRAUD as fraud-risk-service
    participant LEDGER as ledger-service
    participant OUTBOX as Outbox (payment DB)
    participant KAFKA as Kafka
    participant AUDIT as audit-service
    participant NOTIF as notification-service

    C->>WEB: Review & confirm transfer (source, beneficiary, amount)
    WEB->>GW: POST /api/v1/payments  (Idempotency-Key: abc-123)
    GW->>PAY: Forward request + identity context

    PAY->>REDIS: Lookup Idempotency-Key
    alt Key already processed
        REDIS-->>PAY: Cached result
        PAY-->>WEB: Return original result (no reprocessing)
    else New key
        PAY->>PAY: Status = INITIATED → VALIDATING
        PAY->>ACC: Validate source account status, balance, beneficiary
        ACC-->>PAY: OK
        PAY->>KYC: Confirm customer KYC_VERIFIED
        KYC-->>PAY: OK
        PAY->>ACC: Check transaction/daily/beneficiary limits
        ACC-->>PAY: Within limits

        PAY->>PAY: Status = RISK_CHECK
        PAY->>FRAUD: Evaluate risk (amount, velocity, new beneficiary, patterns)
        FRAUD-->>PAY: score + decision (ALLOW / REVIEW / BLOCK)

        alt decision = ALLOW
            PAY->>PAY: Status = PROCESSING
            PAY->>LEDGER: Post double-entry (DEBIT source, CREDIT destination)
            LEDGER-->>PAY: Entries posted, balances updated
            PAY->>PAY: Status = SUCCESS
            PAY->>OUTBOX: Write payment.completed (same DB transaction)
            OUTBOX->>KAFKA: Relay payment.completed
            KAFKA-->>AUDIT: PAYMENT_COMPLETED
            KAFKA-->>NOTIF: PAYMENT_SUCCESS
            PAY->>REDIS: Cache result under Idempotency-Key
            PAY-->>WEB: SUCCESS
        else decision = REVIEW
            PAY->>PAY: Status = FAILED (FRAUD_REVIEW_REQUIRED)
            Note over PAY: Approved release creates a new transaction; the original is not resumed
        else decision = BLOCK
            PAY->>PAY: Status = FAILED
            PAY->>OUTBOX: Write payment.failed
            OUTBOX->>KAFKA: Relay payment.failed
            KAFKA-->>AUDIT: PAYMENT_FAILED
            KAFKA-->>NOTIF: PAYMENT_FAILED
            PAY-->>WEB: FAILED (reason: fraud block)
        end
    end
```

## Validation Order

1. Idempotency-Key lookup (short-circuits everything below on replay)
2. Source account exists, is `ACTIVE`, sufficient available balance
3. Destination/beneficiary is valid and in an eligible status
4. Customer KYC status is `KYC_VERIFIED`
5. Transaction, daily, and beneficiary limits
6. Fraud/risk decision
7. Ledger posting (double-entry, transactional)

## Idempotency

Every mutating call includes `Idempotency-Key`. The key is looked up in Redis before any validation
runs; a repeat of a previously-processed key returns the original stored result rather than
re-executing the payment. See [ADR-0006](../adr/0006-idempotency.md).

## Ledger Posting

Ledger posting happens inside a single database transaction in `ledger-service`, producing a
balanced debit/credit pair. See [ADR-0007](../adr/0007-double-entry-ledger.md) and
[ADR-0008](../adr/0008-concurrency-strategy.md) for the concurrency approach protecting against
double-spend on concurrent transfers from the same source account.

## Reversal / Refund

A `REVERSED` or refund transaction is a **new** payment transaction that posts the inverse
debit/credit pair referencing the original `transactionId` — the original ledger entries are never
mutated or deleted (ledger is append-only, matching [Audit](../../services/audit-service/README.md)
principles).

## Failure Scenarios Covered by This Design

- Duplicate submission (same Idempotency-Key) → original result returned, no duplicate transaction.
- Insufficient balance → `ledger-service` rejects the posting during `PROCESSING` (checked under
  the debit account's row lock, so this is correct even under concurrent transfers) → `FAILED`
  with `failureCode: "INSUFFICIENT_BALANCE"`, no ledger entries created.
- Fraud block → `FAILED`, no ledger entries created, `FRAUD_ALERT_CREATED` audited.
- Medium risk → routed to maker-checker review instead of auto-processing.
- Kafka unavailable at publish time → outbox guarantees the event is not lost (see
  [kafka-architecture.md](kafka-architecture.md)).
