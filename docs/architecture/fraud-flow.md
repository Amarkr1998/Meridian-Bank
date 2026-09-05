# Fraud Detection Flow

> **Implementation status (Phase 9):** `POST /api/v1/risk-assessments` on `fraud-risk-service`,
> the weighted rule engine, the LOW/MEDIUM/HIGH scoring bands, and `payment-service` genuinely
> enforcing the ALLOW/REVIEW/BLOCK decision are all real and implemented exactly as below — proven
> against a real Postgres and the live docker-compose stack; see
> [services/fraud-risk-service/README.md](../../services/fraud-risk-service/README.md). What's
> still a placeholder: a REVIEW decision fails the payment outright
> (`failureCode: "FRAUD_REVIEW_REQUIRED"`) rather than holding it for the Ops Portal review queue
> shown below — that hold/resume workflow belongs to Phase 10's maker-checker infrastructure,
> which doesn't exist yet. `audit-service` writes are the target end-state (Phase 11) — a fraud
> alert's audit trail today is just its own row history in `fraud_alerts`.

## Risk Scoring

```text
0–30    LOW      → ALLOW
31–70   MEDIUM   → REVIEW
71–100  HIGH     → BLOCK
```

Rule categories (configurable, not hardcoded per
[Governance Principles](../governance/governance-principles.md)):

- High transaction amount
- High transaction frequency / velocity
- Multiple recent failed attempts
- New beneficiary + high amount
- Suspicious pattern (e.g. rapid sequential transfers)
- Elevated account risk status

## Sequence Diagram

```mermaid
sequenceDiagram
    participant PAY as payment-service
    participant FRAUD as fraud-risk-service
    participant RULES as Fraud Rule Engine
    participant DB as fraud_alerts / fraud_rules
    participant KAFKA as Kafka
    participant AUDIT as audit-service
    actor RA as Risk Analyst
    participant OPS as Ops Portal

    PAY->>FRAUD: POST /api/v1/risk-assessments (transaction context)
    FRAUD->>RULES: Evaluate configured rules against transaction + history
    RULES-->>FRAUD: Rule hits + weighted score
    FRAUD->>FRAUD: Compute score (0-100), map to LOW/MEDIUM/HIGH

    alt score = LOW
        FRAUD-->>PAY: ALLOW
    else score = MEDIUM
        FRAUD->>DB: Create fraud_alert (status = OPEN, decision = REVIEW)
        FRAUD->>KAFKA: fraud.detected
        KAFKA-->>AUDIT: FRAUD_ALERT_CREATED
        FRAUD-->>PAY: REVIEW
        Note over PAY: Payment held; routed to maker-checker / risk queue
    else score = HIGH
        FRAUD->>DB: Create fraud_alert (status = OPEN, decision = BLOCK)
        FRAUD->>KAFKA: fraud.detected
        KAFKA-->>AUDIT: FRAUD_ALERT_CREATED
        FRAUD-->>PAY: BLOCK
    end

    RA->>OPS: Open fraud dashboard
    OPS->>FRAUD: GET /api/v1/fraud-alerts?status=OPEN
    RA->>OPS: Review / Escalate / Block / Clear
    OPS->>FRAUD: PATCH /api/v1/fraud-alerts/{id}
    FRAUD->>KAFKA: fraud.detected (status update)
    KAFKA-->>AUDIT: audit.event
```

## Roles

Only `RISK_ANALYST`, `COMPLIANCE_OFFICER`, and `ADMIN` may review, escalate, block, or clear a fraud
alert. Every action on an alert is audited.
