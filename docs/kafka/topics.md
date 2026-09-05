# Kafka Topic Catalog

Design principles (outbox, idempotent consumers, retry/DLQ, versioning):
[docs/architecture/kafka-architecture.md](../architecture/kafka-architecture.md).

**Phase 8/9/11/12/13 status:** `customer-kyc-service`, `account-service`, `payment-service`, and
(as of Phase 9) `fraud-risk-service` genuinely publish `customer.created`, `kyc.updated`,
`account.created`, `account.approved`, `payment.initiated`, `payment.completed`,
`payment.failed`, and `fraud.detected` via the transactional outbox pattern — verified against a
real broker, not simulated. `payment.reversed` is not published (no reversal flow exists yet).
**`fraud-risk-service`'s ALLOW/REVIEW/BLOCK decision does *not* reach `payment-service` via this
topic** — despite the catalog row below listing `payment-service` as a consumer (the target
end-state, e.g. for reconciliation/audit use), the actual blocking decision is returned
synchronously from `POST /risk-assessments` — see
[services/fraud-risk-service/README.md](../../services/fraud-risk-service/README.md)'s Trust
boundary section. A dead-letter topic (`<topic>.DLQ`) exists for each topic actually produced so
far, including `audit.event.DLQ` (Phase 11, a *consumer*-side DLQ) and
`reconciliation.completed.DLQ` (Phase 12, producer-side); `notification.requested.DLQ` (Phase 13)
is both, same convergence. As of Phase 12, `ledger-service` genuinely publishes
`reconciliation.completed` once per scheduled reconciliation run — its first use of the outbox
pattern — though nothing consumes that specific topic yet (what's real is that `ledger-service`
separately publishes `audit.event` directly for reconciliation mismatches/resolutions, which
`audit-service` does consume — see
[services/ledger-service/README.md](../../services/ledger-service/README.md)).

As of Phase 11, `audit.event` is genuinely both produced and consumed: `customer-kyc-service`,
`account-service`, `payment-service`, and `fraud-risk-service` each publish it (same outbox
pattern, a duplicated `AuditEventPublisher` per service) for their sensitive actions, and
`audit-service` — this project's first genuine Kafka *consumer* — really consumes it under its own
consumer group, idempotently by `eventId`, with retry/backoff and a real DLQ on exhausted attempts.
See [services/audit-service/README.md](../../services/audit-service/README.md) for exactly which
actions are recorded and which aren't yet.

As of Phase 13, `notification-service` — this project's **second** genuine Kafka consumer — really
consumes `payment.completed`, `payment.failed`, `kyc.updated`, `account.approved`,
`fraud.detected`, and `notification.requested` under its own consumer group, same
idempotent-by-`eventId` + retry/DLQ policy as `audit-service`. `notification.requested` is
genuinely produced for the first time too, by `customer-kyc-service`'s new `SupportRequestService`
on ticket resolution — see [services/notification-service/README.md](../../services/notification-service/README.md)
for exactly which notification types are reachable (two, `LOGIN_ALERT`/`SECURITY_ALERT`, remain
defined-but-unreachable since `auth-service` has no Kafka wiring at all). `customer.created` and
`account.created` still have no consumer.

## Topics

| Topic | Producer | Consumers | Purpose |
|---|---|---|---|
| `customer.created` | `customer-kyc-service` | *(none yet)* | New customer registered |
| `kyc.updated` | `customer-kyc-service` | `account-service` (aspirational), `notification-service` (real, Phase 13), `audit-service` (real, Phase 11) | KYC status transition |
| `account.created` | `account-service` | *(none yet)* | Account opening request created |
| `account.approved` | `account-service` | `notification-service` (real, Phase 13), `audit-service` (real, Phase 11) | Account activated |
| `payment.initiated` | `payment-service` | `fraud-risk-service` (aspirational — reached synchronously instead, see status note above) | Payment entered the pipeline |
| `payment.completed` | `payment-service` | `notification-service` (real, Phase 13), `audit-service` (real, Phase 11) | Payment succeeded, ledger posted |
| `payment.failed` | `payment-service` | `notification-service` (real, Phase 13), `audit-service` (real, Phase 11) | Payment failed validation/risk/processing |
| `payment.reversed` | *(not yet produced — no reversal flow)* | `notification-service`, `audit-service` | Payment reversed/refunded |
| `fraud.detected` | `fraud-risk-service` | `payment-service` (aspirational), `notification-service` (real, Phase 13), `audit-service` (real, Phase 11) | Fraud/AML alert raised or updated |
| `notification.requested` | `customer-kyc-service` (real, Phase 13 — support ticket resolution) | `notification-service` (real, Phase 13) | Generic notification dispatch request |
| `audit.event` | `customer-kyc-service`, `account-service`, `payment-service`, `fraud-risk-service`, `ledger-service` (real, Phase 11/12) | `audit-service` (real, Phase 11) | Sensitive action audit record |
| `reconciliation.completed` | `ledger-service` / reconciliation job (real, Phase 12) | *(none yet)* | A reconciliation run finished |

## Event Envelope Convention

Every event carries a common envelope in addition to its payload:

```json
{
  "eventId": "uuid",
  "eventType": "payment.completed",
  "eventVersion": 1,
  "occurredAt": "2026-01-01T00:00:00Z",
  "correlationId": "uuid",
  "producedBy": "payment-service",
  "payload": { }
}
```

- `eventId` is used by consumers for idempotent de-duplication.
- `eventVersion` supports additive schema evolution; breaking changes require a new `eventType` or a
  major version bump handled explicitly by consumers.
- `correlationId` ties the event back to the originating API request for tracing across the audit
  trail and observability tooling.

## Planned Implementation Phase

Phase 8 (Kafka + Outbox + Retry + DLQ). Individual topics come online incrementally as their owning
service is implemented in earlier phases (producers may exist before all consumers do). Phase 11
brought the first real consumer online (`audit-service`, consuming `audit.event`). Phase 12 brought
`ledger-service`'s own outbox online (`reconciliation.completed`), its first use of the pattern.
Phase 13 brought the second real consumer online (`notification-service`) and the first real
producer to `notification.requested` (`customer-kyc-service`'s support-ticket resolution).
