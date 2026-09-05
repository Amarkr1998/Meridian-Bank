-- Meridian Bank — ledger-service initial schema
-- Owns: ledger_entries, balances. This is the system of record for money movement — see
-- docs/adr/0007-double-entry-ledger.md. account_id/transaction_id are opaque UUIDs matching
-- identities minted in account-service/payment-service — not cross-database foreign keys
-- (see docs/adr/0003-postgresql-system-of-record.md).

-- Append-only: never UPDATEd or DELETEd. A reversal/refund posts a new, inverse pair of entries
-- referencing a new transaction_id — see docs/architecture/payment-flow.md.
CREATE TABLE ledger_entries (
    ledger_entry_id   UUID PRIMARY KEY,
    transaction_id    UUID          NOT NULL,
    account_id        UUID          NOT NULL,
    entry_type        VARCHAR(10)   NOT NULL,
    amount            NUMERIC(18,2) NOT NULL,
    currency          VARCHAR(3)    NOT NULL,
    reference         VARCHAR(200),
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT chk_ledger_entries_type CHECK (entry_type IN ('DEBIT', 'CREDIT')),
    CONSTRAINT chk_ledger_entries_amount_positive CHECK (amount > 0)
);

CREATE INDEX idx_ledger_entries_transaction_id ON ledger_entries (transaction_id);
CREATE INDEX idx_ledger_entries_account_id ON ledger_entries (account_id);
-- Idempotency for the posting endpoint: a transaction is posted at most once per account side.
CREATE UNIQUE INDEX uq_ledger_entries_transaction_account_type_idx
    ON ledger_entries (transaction_id, account_id, entry_type);

-- One row per account — materialized for fast reads, but always derived from (and kept
-- consistent with, in the same DB transaction as) ledger_entries, never mutated independently.
-- available_balance and ledger_balance are always equal in this phase: there is no
-- pending/hold concept yet (see ledger-service/README.md).
CREATE TABLE balances (
    account_id          UUID PRIMARY KEY,
    available_balance    NUMERIC(18,2) NOT NULL DEFAULT 0,
    ledger_balance        NUMERIC(18,2) NOT NULL DEFAULT 0,
    currency              VARCHAR(3)    NOT NULL,
    updated_at            TIMESTAMPTZ   NOT NULL DEFAULT now(),
    version               BIGINT        NOT NULL DEFAULT 0
);
