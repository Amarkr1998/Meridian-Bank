# ADR-0002: Kafka as the Event Backbone

**Status:** Accepted
**Date:** 2026-09-04

## Problem

Several workflows in Meridian Bank are naturally asynchronous and fan out to multiple interested
services without the originator needing to block on them: notifying a customer after a payment
completes, recording an audit event, reacting to a KYC status change from `account-service`. Coupling
these through synchronous REST calls would make the originating service responsible for the
availability of every downstream consumer, which is both unrealistic for banking event flows and
brittle.

## Decision

Use Apache Kafka as the asynchronous event backbone for cross-service, fire-and-forget-from-the-
producer's-perspective workflows: `customer.created`, `kyc.updated`, `account.created`,
`account.approved`, `payment.*`, `fraud.detected`, `notification.requested`, `audit.event`,
`reconciliation.completed`. Synchronous REST remains for calls where the caller needs an immediate
answer to proceed (e.g. `payment-service` checking account balance).

## Alternatives Considered

- **Synchronous REST fan-out** from producers to every consumer — rejected: couples producer
  availability to every consumer's uptime, and does not demonstrate event-driven architecture.
- **RabbitMQ / other message broker** — viable alternative, but Kafka's log-based retention,
  consumer groups, and replay capability better fit the audit/reconciliation use cases and are the
  more common choice in real banking event platforms, making it the stronger portfolio signal.

## Trade-offs

Gains: decoupling, replayability, natural fit for audit/notification fan-out, consumer group
scaling. Costs: eventual consistency between services, additional operational component to run
locally, requires idempotent consumers and outbox discipline to avoid lost/duplicated events.

## Consequences

- Producers never publish directly inside a request handler — see
  [ADR-0005](0005-outbox-pattern.md).
- Consumers must be idempotent and handle at-least-once delivery — see
  [docs/architecture/kafka-architecture.md](../architecture/kafka-architecture.md).
- Full topic catalog: [docs/kafka/topics.md](../kafka/topics.md).
