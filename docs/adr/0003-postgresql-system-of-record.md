# ADR-0003: PostgreSQL as the System of Record

**Status:** Accepted
**Date:** 2026-09-04

## Problem

Banking data — customers, accounts, ledger entries, transactions — is inherently relational, has
strong consistency and integrity requirements (foreign keys, check constraints, transactional
guarantees), and must support ACID transactions for financial correctness (e.g. atomic double-entry
posting). The persistence choice needs to make correctness the default, not something bolted on.

## Decision

Use PostgreSQL as the system of record for every service, with one schema per service (see
[ADR-0001](0001-microservices-architecture.md)), Flyway-managed migrations per service, and proper
transactional boundaries around financial operations (see
[ADR-0007](0007-double-entry-ledger.md), [ADR-0008](0008-concurrency-strategy.md)).

## Alternatives Considered

- **NoSQL document store** (e.g. MongoDB) — rejected for core banking data: weaker native support
  for multi-row ACID transactions and referential integrity, both essential for ledger correctness.
- **Separate database technology per service** (polyglot persistence) — rejected for this project's
  scope: would add operational complexity without a corresponding demonstration benefit; PostgreSQL
  is well suited to every service's data shape here.

## Trade-offs

Gains: strong consistency guarantees, mature tooling (Flyway, JPA/Hibernate), a single technology to
operate locally. Costs: schema-per-service still requires deliberate discipline to avoid accidental
cross-schema coupling; horizontal write scaling is harder than with some NoSQL stores (not a concern
at this project's demo scale).

## Consequences

- No service reads or writes another service's schema directly — cross-service data access goes
  through APIs or consumed Kafka events, never a shared connection.
- Domain model / ERD: [docs/database/domain-model.md](../database/domain-model.md).
