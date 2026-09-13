# Interview Guide

## Thirty-second summary

Meridian Bank is a fictional digital-banking platform built as nine Spring Boot services and one
React application containing customer and operations portals. It demonstrates correctness-heavy
banking patterns: idempotent payment orchestration, a concurrency-safe double-entry ledger,
fraud/AML decisions, maker-checker governance, transactional outboxes, Kafka consumers, audit,
reconciliation, observability, automated tests, and reproducible local deployment.

## Architecture decisions worth defending

- **Nine bounded services, not service-per-table:** each service owns its schema and cross-service
  access happens through APIs or events.
- **REST plus Kafka:** REST is used when a request needs an immediate decision; Kafka carries
  non-blocking audit, notification, and state-propagation work.
- **Transactional outbox:** avoids the database/Kafka dual-write failure window.
- **Double-entry ledger:** balances are derived from immutable debit and credit entries; posting
  locks accounts in stable order to prevent overspend and deadlock.
- **Defense in depth:** gateway validation reduces bad traffic, while each downstream service
  remains the final RBAC and ownership boundary.
- **Maker-checker:** high-impact actions require two distinct authenticated staff identities.
- **Local-only delivery:** CI proves deployability and packages tagged releases without claiming a
  production environment that does not exist.

## Questions to expect

### Why microservices for a portfolio project?

To demonstrate bounded ownership, distributed consistency, independent security boundaries, and
failure handling. The trade-off is operational weight; a modular monolith would be the sensible
starting point for many real teams.

### How do you prevent duplicate payments?

The API requires an idempotency key, stores a fingerprint of the request, and returns the original
result for an exact replay. Reusing the key with a different payload is rejected. Concurrency tests
exercise the race against real Redis.

### Can Kafka failure lose a committed business event?

The business mutation and an outbox row commit in one database transaction. A scheduled publisher
retries delivery and dead-letters exhausted events. Consumers deduplicate by event ID.

### How is double spending prevented?

Ledger posting validates the debit under a database lock, acquires account locks in deterministic
order, and commits both sides together. Concurrent integration tests verify both overdraft and
deadlock behavior against PostgreSQL.

### What would you change for production?

Use managed HA infrastructure, asymmetric or externally managed signing keys, httpOnly secure
cookies, TLS everywhere, a secrets manager, immutable image registry, per-pod metrics discovery,
tracing, real notification/KYC/payment providers, backup and disaster recovery, full dependency
and container scanning, performance testing, and audited regulatory controls.

## Strong closing statement

The project is intentionally honest about its boundaries. Its value is not the number of screens;
it is that the critical invariants—authorization, idempotency, balanced postings, concurrency,
approval separation, and durable event intent—are represented in code and tested against real
infrastructure.

