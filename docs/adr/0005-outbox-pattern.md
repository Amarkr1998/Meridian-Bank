# ADR-0005: Transactional Outbox Pattern

**Status:** Accepted
**Date:** 2026-09-04

## Problem

A service that writes a business record (e.g. a completed payment) and then separately calls
`kafka.publish(...)` has a correctness gap: if the process crashes or Kafka is unavailable between
the database commit and the publish call, the event is silently lost even though the business
transaction succeeded — or, conversely, the event might publish while the database transaction later
rolls back. Given `payment.completed`, `audit.event`, and similar events drive downstream
notification, audit, and reconciliation behavior, losing them silently is unacceptable.

## Decision

Every service that publishes Kafka events writes its business record and a corresponding row in its
own `outbox_events` table within the **same** database transaction. A separate outbox publisher
process polls unsent outbox rows and relays them to Kafka, marking them sent only after a successful
publish. See [docs/architecture/kafka-architecture.md](../architecture/kafka-architecture.md).

## Alternatives Considered

- **Publish directly in the request handler after commit** — rejected: the crash/unavailability
  window between commit and publish is exactly the failure mode this pattern exists to close.
- **Two-phase commit between the database and Kafka** — rejected: Kafka does not participate in XA
  transactions in a way that's practical here, and 2PC adds significant complexity and latency for
  a portfolio project's demo scale.
- **Change Data Capture (CDC) off the WAL** (e.g. Debezium) — a legitimate production alternative
  that removes the need for an explicit outbox table, but adds infrastructure (Kafka Connect) beyond
  this project's scope; an application-level outbox is more transparent for demonstrating the
  pattern's mechanics.

## Trade-offs

Gains: at-least-once event delivery guaranteed to align with the committed business transaction, no
silent event loss. Costs: consumers must be idempotent (at-least-once, not exactly-once, delivery is
still possible if the publisher marks-sent fails after a successful publish); added polling/publisher
component per service.

## Consequences

- Every event-producing service needs an `outbox_events` table and a publisher job/thread.
- Consumers must de-duplicate by `eventId` — see [ADR-0002](0002-kafka-event-backbone.md).
- Implemented in Phase 8, after the services and events it carries already exist from earlier
  phases.
