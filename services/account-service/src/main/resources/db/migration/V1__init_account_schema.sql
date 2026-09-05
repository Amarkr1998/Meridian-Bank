-- Meridian Bank — account-service initial schema
-- Owns: account_opening_requests, accounts, account_status_history.
-- customer_id matches the id minted by auth-service/customer-kyc-service at registration —
-- not a cross-database foreign key, just a matching UUID (see
-- docs/adr/0003-postgresql-system-of-record.md).

CREATE TABLE account_opening_requests (
    id                 UUID PRIMARY KEY,
    customer_id        UUID          NOT NULL,
    account_type       VARCHAR(20)   NOT NULL,
    status             VARCHAR(20)   NOT NULL DEFAULT 'ACCOUNT_REQUESTED',
    requested_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    reviewed_at        TIMESTAMPTZ,
    reviewed_by        UUID,
    rejection_reason   VARCHAR(500),
    account_id         UUID,
    CONSTRAINT chk_account_requests_type CHECK (account_type IN ('SAVINGS', 'CURRENT')),
    CONSTRAINT chk_account_requests_status CHECK (
        status IN ('ACCOUNT_REQUESTED', 'UNDER_REVIEW', 'APPROVED', 'REJECTED')
    )
);

CREATE INDEX idx_account_requests_customer_id ON account_opening_requests (customer_id);
CREATE INDEX idx_account_requests_status ON account_opening_requests (status);

CREATE TABLE accounts (
    id                       UUID PRIMARY KEY,
    customer_id              UUID          NOT NULL,
    account_number           VARCHAR(20)   NOT NULL,
    account_type             VARCHAR(20)   NOT NULL,
    status                   VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    currency                 VARCHAR(3)    NOT NULL DEFAULT 'USD',
    per_transaction_limit    NUMERIC(18,2) NOT NULL,
    daily_limit              NUMERIC(18,2) NOT NULL,
    opened_at                TIMESTAMPTZ   NOT NULL DEFAULT now(),
    closed_at                TIMESTAMPTZ,
    created_at               TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ   NOT NULL DEFAULT now(),
    version                  BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT chk_accounts_type CHECK (account_type IN ('SAVINGS', 'CURRENT')),
    CONSTRAINT chk_accounts_status CHECK (status IN ('ACTIVE', 'FROZEN', 'BLOCKED', 'CLOSED')),
    CONSTRAINT chk_accounts_limits_non_negative CHECK (per_transaction_limit >= 0 AND daily_limit >= 0)
);

CREATE UNIQUE INDEX uq_accounts_account_number_idx ON accounts (account_number);
CREATE INDEX idx_accounts_customer_id ON accounts (customer_id);
CREATE INDEX idx_accounts_status ON accounts (status);

CREATE TABLE account_status_history (
    id             UUID PRIMARY KEY,
    account_id     UUID          NOT NULL REFERENCES accounts (id),
    old_status     VARCHAR(20)   NOT NULL,
    new_status     VARCHAR(20)   NOT NULL,
    reason         VARCHAR(500),
    changed_by     UUID          NOT NULL,
    changed_at     TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX idx_account_status_history_account_id ON account_status_history (account_id);
