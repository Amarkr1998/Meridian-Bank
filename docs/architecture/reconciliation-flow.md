# Reconciliation Flow

> **Implementation status (Phase 12):** the compare/classify loop below is real — see
> [Reconciliation Design](../reconciliation/reconciliation-design.md)'s implementation-status note
> for exactly what's real and what (the Ops Portal) is still a future phase. One correction to the
> sequence diagram below: the external feed is not a live external system a scheduled job "fetches
> from" — it's synthesized on demand, lazily, the first time the job encounters a transaction with
> no counterpart yet (`ExternalFeedGenerator`), not pre-fetched for a period up front.

Full design rationale: [Reconciliation Design](../reconciliation/reconciliation-design.md).

## Flow

```text
Internal Ledger (ledger_entries)
            ↓
        Compare
            ↓
Synthetic External Records (reconciliation_records source)
            ↓
     MATCH / MISMATCH
```

Statuses: `MATCHED`, `MISMATCHED`, `PENDING`, `INVESTIGATION`, `RESOLVED`.

## Sequence Diagram

```mermaid
sequenceDiagram
    participant SCHED as Scheduled Job
    participant LEDGER as ledger-service
    participant EXT as Synthetic External Feed
    participant RECON as Reconciliation Engine
    participant DB as reconciliation_records
    participant KAFKA as Kafka
    participant AUDIT as audit-service
    actor OP as Operations Staff
    participant OPS as Ops Portal

    SCHED->>LEDGER: Fetch ledger entries for period
    SCHED->>EXT: Fetch synthetic external transaction records for period
    SCHED->>RECON: Compare(internal, external)

    loop for each record pair
        alt Amounts & references match
            RECON->>DB: status = MATCHED
        else Mismatch found
            RECON->>DB: status = MISMATCHED
        else No counterpart found yet
            RECON->>DB: status = PENDING
        end
    end

    RECON->>KAFKA: reconciliation.completed
    KAFKA-->>AUDIT: audit.event

    OP->>OPS: Open reconciliation dashboard
    OPS->>DB: GET summary (total / matched / mismatched / pending / resolved)
    OP->>OPS: Investigate a MISMATCHED record
    OPS->>DB: status = INVESTIGATION
    OP->>OPS: Resolve (with resolution notes)
    OPS->>DB: status = RESOLVED
    OPS->>KAFKA: audit.event
    KAFKA-->>AUDIT: audit trail entry
```

## Notes

- External records are always synthetic, generated locally for demo purposes — never real external
  bank data or a real third-party integration.
- Reconciliation never mutates `ledger_entries`; it only ever produces/updates
  `reconciliation_records`, keeping the ledger the single append-only source of truth.
