# ADR-0001: Microservices Architecture

**Status:** Accepted
**Date:** 2026-09-04

## Problem

Meridian Bank needs to demonstrate enterprise banking engineering practice: independent
deployability, bounded contexts owned by clear teams, and the operational patterns (service-to-service
auth, event-driven integration, per-service data ownership) that show up in real banking platforms.
A single monolith would be simpler to build but would not exercise these patterns, and an
unconstrained microservice count would turn the project into integration overhead without
proportional learning value.

## Decision

Build nine fixed services, each owning a clear banking bounded context: `api-gateway`,
`auth-service`, `customer-kyc-service`, `account-service`, `payment-service`, `ledger-service`,
`fraud-risk-service`, `notification-service`, `audit-service`. No new service is added purely to
increase the count — a new capability first asks whether it fits an existing service's bounded
context.

## Alternatives Considered

- **Modular monolith** — simpler to build and test, but does not demonstrate inter-service
  communication, per-service deployment, or the distributed-systems patterns (outbox, idempotent
  consumers, eventual consistency) that are core learning goals of this project.
- **Fine-grained microservices per entity** (e.g. separate services for beneficiaries, statements,
  limits) — rejected as unnecessary fragmentation; these belong inside `account-service`'s bounded
  context and would multiply operational overhead without demonstrating additional patterns.

## Trade-offs

Gains: realistic distributed-systems patterns, clear service boundaries, independent scalability
demonstration. Costs: more infrastructure to run locally, cross-service transaction consistency
must be handled explicitly (outbox, eventual consistency) rather than via a local DB transaction.

## Consequences

- Each service owns its own PostgreSQL schema (see [ADR-0003](0003-postgresql-system-of-record.md)).
- Cross-service consistency for anything spanning services relies on the outbox + Kafka pattern
  (see [ADR-0005](0005-outbox-pattern.md)), not distributed transactions.
- Service boundaries fixed in this ADR should be treated as stable; splitting or merging a service
  later is a decision significant enough to warrant its own ADR.
