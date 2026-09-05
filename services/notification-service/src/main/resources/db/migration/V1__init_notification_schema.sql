-- Meridian Bank — notification-service initial schema (Phase 13).
--
-- One row per in-app notification. event_id is the producing service's Kafka envelope eventId
-- (see docs/kafka/topics.md) — unique here so a redelivered message (at-least-once Kafka
-- delivery) is a no-op, not a duplicate notification, same idempotency pattern as
-- audit-service's audit_events.
CREATE TABLE notifications (
    id           UUID PRIMARY KEY,
    event_id     UUID          NOT NULL,
    customer_id  UUID          NOT NULL,
    type         VARCHAR(30)   NOT NULL,
    title        VARCHAR(200)  NOT NULL,
    body         VARCHAR(1000) NOT NULL,
    read         BOOLEAN       NOT NULL DEFAULT false,
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_notifications_event_id UNIQUE (event_id)
);

CREATE INDEX idx_notifications_customer_id ON notifications (customer_id);
CREATE INDEX idx_notifications_customer_read ON notifications (customer_id, read);
