-- Meridian Bank — ledger-service outbox (Phase 12, wired in this phase for
-- reconciliation.completed + audit.event — see docs/adr/0005-outbox-pattern.md). Same
-- OutboxWriter/OutboxPublisher implementation as every other outbox-using service (duplicated,
-- not shared). OutboxPublisher relays PENDING rows to Kafka and marks them SENT, or FAILED after
-- exhausting retries (see its Javadoc for the retry/DLQ policy).

CREATE TABLE outbox_events (
    id              UUID PRIMARY KEY,
    aggregate_type  VARCHAR(50)   NOT NULL,
    aggregate_id    UUID          NOT NULL,
    event_type      VARCHAR(100)  NOT NULL,
    payload         TEXT          NOT NULL,
    status          VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    attempts        INT           NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    next_attempt_at TIMESTAMPTZ   NOT NULL DEFAULT now(),
    sent_at         TIMESTAMPTZ,
    CONSTRAINT chk_outbox_events_status CHECK (status IN ('PENDING', 'SENT', 'FAILED'))
);

-- Supports OutboxPublisher's poll query: PENDING rows whose next_attempt_at has arrived, oldest
-- first.
CREATE INDEX idx_outbox_events_status_next_attempt ON outbox_events (status, next_attempt_at, created_at);
