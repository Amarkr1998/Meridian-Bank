# ADR-0012: Append-Only Audit Architecture

**Status:** Accepted
**Date:** 2026-09-04

## Problem

Banking systems must be able to answer "who did what, when, and with what result" for every
sensitive operation, both for operational investigation and as a governance control in its own
right. If audit data can be edited or deleted by the same users whose actions it records, it isn't a
trustworthy control.

## Decision

Centralize audit recording in `audit-service`, which consumes an `audit.event` Kafka topic that
every other service publishes to (via the outbox pattern, see [ADR-0005](0005-outbox-pattern.md))
whenever a sensitive action occurs. `audit-service` exposes only read (and ingest) operations —
there is no update or delete API, and normal business/operations users have no path to modify audit
history. See [docs/architecture](../architecture) flow docs and
[audit-service](../../services/audit-service/README.md).

## Alternatives Considered

- **Each service logs its own audit table, no central service** — rejected: fragments the audit
  trail across nine schemas, making cross-service investigation (e.g. tracing a payment from
  initiation through fraud review to approval) much harder, and duplicates the "must be append-only"
  discipline nine times instead of once.
- **Audit via application logs only (no structured store)** — rejected: logs are not queryable or
  reportable the way the operations audit UI (`/ops/audit`) needs, and are far easier to lose or
  rotate away than a durable table.

## Trade-offs

Gains: single, trustworthy, queryable audit trail; consistent event shape across all services.
Costs: `audit-service` becomes a dependency every other service relies on for compliance
completeness (mitigated by the outbox pattern ensuring events aren't lost even if `audit-service` is
briefly down — they queue in Kafka).

## Consequences

- `audit_events` supports insert and read only at the database/API level; no update or delete
  endpoint is ever built for normal roles.
- Every phase from Phase 3 onward that introduces a sensitive action is responsible for emitting the
  corresponding `audit.event`, even though `audit-service` itself isn't built until Phase 11 —
  events queue in Kafka in the interim once Phase 8's outbox/Kafka infrastructure exists.

## Implementation notes (Phase 11)

- Retrofitted, not built ahead of schedule: `audit.event` publishing was **not** actually wired
  into the four producing services during Phases 3–10 (only their own domain events were — see
  docs/kafka/topics.md's Phase 8/9 status note as it stood before this phase). Phase 11 both built
  `audit-service` and retrofitted `audit.event` publishing into `customer-kyc-service`,
  `account-service`, `payment-service`, and `fraud-risk-service` for their existing sensitive
  actions — see each service's README for exactly which actions now emit it. This is a genuine gap
  from how this ADR originally described the rollout, called out rather than glossed over.
- **Not every sensitive action in the system is covered yet** — the four services' READMEs are
  explicit about what is and isn't recorded (e.g. beneficiary block/unblock and most account
  lifecycle transitions beyond freeze are not individually audited). This is a real, working
  pipeline for a defined action set, not a claim of exhaustive coverage.
- **Append-only is enforced at the application/repository layer, not a database trigger** — no
  `UPDATE`/`DELETE` method exists on `AuditEventRepository`, and no such HTTP endpoint is ever
  built (proven by a live test asserting `405` on `POST /audit-events`, even with an `ADMIN`
  token). This matches this project's existing precedent for `ledger-service`'s `ledger_entries`
  (also convention-enforced, not trigger-enforced — see its README) rather than introducing a new,
  stronger mechanism used nowhere else in the codebase.
- **Idempotency is by the Kafka envelope's `eventId`**, backstopped by a unique constraint on
  `audit_events.event_id` — necessary because Kafka delivery is at-least-once, not exactly-once;
  see `AuditIngestService`'s Javadoc and `docs/architecture/kafka-architecture.md`.
