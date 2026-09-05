-- Meridian Bank — payment-service initial schema
-- Owns: transactions, transaction_status_history.
-- customer_id / source_account_id / beneficiary_id / destination_account_id are opaque UUIDs
-- matching identities minted in auth-service/account-service — not cross-database foreign keys
-- (see docs/adr/0003-postgresql-system-of-record.md).
--
-- No balance/ledger columns here by design: this service does not move real money yet — see
-- docs/adr/0007-double-entry-ledger.md and payment-service/README.md. outbox_events is not
-- created yet either; that lands with Phase 8's Kafka/outbox work.

CREATE TABLE transactions (
    id                     UUID PRIMARY KEY,
    customer_id            UUID          NOT NULL,
    source_account_id      UUID          NOT NULL,
    beneficiary_id         UUID          NOT NULL,
    destination_account_id UUID,
    amount                 NUMERIC(18,2) NOT NULL,
    currency               VARCHAR(3)    NOT NULL,
    purpose                VARCHAR(200),
    status                 VARCHAR(20)   NOT NULL DEFAULT 'INITIATED',
    failure_code           VARCHAR(50),
    failure_reason         VARCHAR(500),
    idempotency_key        VARCHAR(255)  NOT NULL,
    request_fingerprint    VARCHAR(64)   NOT NULL,
    created_at             TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ   NOT NULL DEFAULT now(),
    completed_at           TIMESTAMPTZ,
    CONSTRAINT chk_transactions_status CHECK (
        status IN ('INITIATED', 'VALIDATING', 'RISK_CHECK', 'PROCESSING', 'SUCCESS', 'FAILED')
    ),
    CONSTRAINT chk_transactions_amount_positive CHECK (amount > 0)
);

-- The durable safety net behind the Redis-backed fast path (see IdempotencyService /
-- docs/adr/0006-idempotency.md): even if Redis state is lost mid-flight, a genuine duplicate
-- INSERT for the same customer+key fails here rather than creating a second transaction.
CREATE UNIQUE INDEX uq_transactions_customer_idempotency_key_idx ON transactions (customer_id, idempotency_key);

CREATE INDEX idx_transactions_customer_id ON transactions (customer_id);
CREATE INDEX idx_transactions_source_account_id ON transactions (source_account_id);
CREATE INDEX idx_transactions_status ON transactions (status);
-- Supports the daily-limit running-total query (source account + today's SUCCESS transactions).
CREATE INDEX idx_transactions_source_account_status_created ON transactions (source_account_id, status, created_at);

CREATE TABLE transaction_status_history (
    id               UUID PRIMARY KEY,
    transaction_id   UUID          NOT NULL REFERENCES transactions (id),
    old_status       VARCHAR(20)   NOT NULL,
    new_status       VARCHAR(20)   NOT NULL,
    reason           VARCHAR(500),
    changed_at       TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX idx_transaction_status_history_transaction_id ON transaction_status_history (transaction_id);
