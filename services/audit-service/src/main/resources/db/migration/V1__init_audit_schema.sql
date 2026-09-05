-- Meridian Bank — audit-service initial schema
-- Owns: audit_events. Append-only by convention — the same precedent as ledger-service's
-- ledger_entries (see its README): no UPDATE/DELETE repository method or HTTP endpoint is ever
-- built for this table, for any role, normal or staff. See docs/adr/0012-audit-architecture.md.

-- event_id is the envelope's eventId (see docs/kafka/topics.md) — the producer-assigned identity
-- of the event, NOT this row's own primary key. It is what makes the Kafka consumer idempotent
-- under at-least-once delivery: a redelivered message with the same event_id is silently a no-op
-- (see AuditIngestService), rather than a duplicate audit row.
CREATE TABLE audit_events (
    id              UUID PRIMARY KEY,
    event_id        UUID          NOT NULL,
    event_type      VARCHAR(50)   NOT NULL,
    occurred_at     TIMESTAMPTZ   NOT NULL,
    correlation_id  VARCHAR(100),
    produced_by     VARCHAR(50)   NOT NULL,
    actor_id        UUID,
    actor_role      VARCHAR(30),
    action          VARCHAR(50)   NOT NULL,
    resource_type   VARCHAR(50)   NOT NULL,
    resource_id     UUID,
    result          VARCHAR(30)   NOT NULL,
    detail          VARCHAR(1000),
    received_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_audit_events_event_id UNIQUE (event_id)
);

CREATE INDEX idx_audit_events_actor ON audit_events (actor_id);
CREATE INDEX idx_audit_events_action ON audit_events (action);
CREATE INDEX idx_audit_events_resource ON audit_events (resource_type, resource_id);
CREATE INDEX idx_audit_events_correlation ON audit_events (correlation_id);
CREATE INDEX idx_audit_events_occurred_at ON audit_events (occurred_at);
CREATE INDEX idx_audit_events_produced_by ON audit_events (produced_by);
