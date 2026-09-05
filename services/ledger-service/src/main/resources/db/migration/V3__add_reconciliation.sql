-- Meridian Bank — reconciliation (Phase 12). See docs/reconciliation/reconciliation-design.md and
-- docs/architecture/reconciliation-flow.md.
--
-- external_transactions is a synthetic, locally-generated stand-in for an external record of the
-- same money movement — never real external bank data or a live third-party feed (see
-- ExternalFeedGenerator's Javadoc for exactly how each row is deterministically synthesized).
-- reference mirrors a ledger_entries.transaction_id when the generator decides this transaction
-- has an external counterpart at all (a PENDING case has none yet, by design).
CREATE TABLE external_transactions (
    id              UUID PRIMARY KEY,
    reference       UUID          NOT NULL,
    amount          NUMERIC(18,2) NOT NULL,
    currency        VARCHAR(3)    NOT NULL,
    external_status VARCHAR(20)   NOT NULL DEFAULT 'SETTLED',
    generated_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    -- The row exists from the moment it's generated, but the reconciliation job treats it as "not
    -- yet arrived" until now() reaches this timestamp — see ExternalFeedGenerator's Javadoc for
    -- why this is what actually produces the PENDING -> MATCHED/MISMATCHED transition over time,
    -- deterministically, without re-rolling anything on a later run.
    available_at    TIMESTAMPTZ   NOT NULL,
    CONSTRAINT uq_external_transactions_reference UNIQUE (reference)
);

CREATE INDEX idx_external_transactions_available_at ON external_transactions (available_at);

-- One row per internal ledger transaction_id under reconciliation. Never mutates ledger_entries —
-- see reconciliation-design.md ("Never mutates the ledger").
CREATE TABLE reconciliation_records (
    id                       UUID PRIMARY KEY,
    transaction_id           UUID          NOT NULL,
    internal_amount          NUMERIC(18,2) NOT NULL,
    internal_currency        VARCHAR(3)    NOT NULL,
    external_transaction_id  UUID,
    external_amount          NUMERIC(18,2),
    external_currency        VARCHAR(3),
    status                   VARCHAR(20)   NOT NULL,
    mismatch_reason          VARCHAR(500),
    investigated_by          UUID,
    investigated_at          TIMESTAMPTZ,
    resolved_by              UUID,
    resolved_at              TIMESTAMPTZ,
    resolution_notes         VARCHAR(1000),
    run_id                   UUID          NOT NULL,
    created_at               TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_reconciliation_records_transaction_id UNIQUE (transaction_id),
    CONSTRAINT chk_reconciliation_records_status
        CHECK (status IN ('MATCHED', 'MISMATCHED', 'PENDING', 'INVESTIGATION', 'RESOLVED'))
);

CREATE INDEX idx_reconciliation_records_status ON reconciliation_records (status);
CREATE INDEX idx_reconciliation_records_run_id ON reconciliation_records (run_id);
