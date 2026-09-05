# Architecture Decision Records

ADRs capture the significant, hard-to-reverse structural decisions behind Meridian Bank, with the
alternatives considered and the trade-offs accepted. Use [template.md](template.md) for new ADRs.

| ADR | Title | Status |
|---|---|---|
| [0001](0001-microservices-architecture.md) | Microservices Architecture | Accepted |
| [0002](0002-kafka-event-backbone.md) | Kafka as the Event Backbone | Accepted |
| [0003](0003-postgresql-system-of-record.md) | PostgreSQL as the System of Record | Accepted |
| [0004](0004-redis-for-idempotency-and-rate-limiting.md) | Redis for Idempotency, Rate Limiting & Short-Lived Data | Accepted |
| [0005](0005-outbox-pattern.md) | Transactional Outbox Pattern | Accepted |
| [0006](0006-idempotency.md) | Idempotency via Idempotency-Key | Accepted |
| [0007](0007-double-entry-ledger.md) | Double-Entry Ledger | Accepted |
| [0008](0008-concurrency-strategy.md) | Concurrency Strategy for Financial State | Accepted |
| [0009](0009-api-gateway.md) | API Gateway as Single Entry Point | Accepted |
| [0010](0010-rbac.md) | Role-Based Access Control | Accepted |
| [0011](0011-maker-checker.md) | Maker-Checker (Four-Eyes) Control | Accepted |
| [0012](0012-audit-architecture.md) | Append-Only Audit Architecture | Accepted |
| [0013](0013-reconciliation.md) | Ledger Reconciliation Against Synthetic External Records | Accepted |
| [0014](0014-circuit-breaker.md) | Circuit Breaker / Resilience Policy | Accepted |
| [0015](0015-observability.md) | Observability via Micrometer, Prometheus, and Grafana | Accepted |

ADRs 0001–0014 were made in Phase 0 (architecture) even though most of them are only implemented
in later phases — they exist to keep implementation consistent with the intended design rather
than being decided ad hoc mid-phase. ADR-0015 is the first exception: it was authored during
Phase 16 itself, once the actual shape of the observability pipeline (which metrics, which
dashboard approach, whether to add business metrics) was a real decision to make rather than one
worth pre-planning in the abstract back in Phase 0. If an implementation phase surfaces a reason
to revisit an existing ADR, update it (status → `Superseded` with a link to the new one) rather
than silently drifting from it.
