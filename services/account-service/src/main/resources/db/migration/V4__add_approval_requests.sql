-- Meridian Bank — account-service maker-checker (Phase 10)
-- Gates the BLOCK_ACCOUNT action — see docs/adr/0011-maker-checker.md. A single decision round
-- only (no "Return for revision" cycle — see account-service/README.md), so one row per request
-- is sufficient; no separate approval_actions history table.

CREATE TABLE approval_requests (
    id              UUID PRIMARY KEY,
    action_type     VARCHAR(30)   NOT NULL,
    resource_id     UUID          NOT NULL,
    reason          VARCHAR(500),
    status          VARCHAR(20)   NOT NULL DEFAULT 'PENDING_APPROVAL',
    requested_by    UUID          NOT NULL,
    requested_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    decided_by      UUID,
    decided_at      TIMESTAMPTZ,
    decision_notes  VARCHAR(1000),
    CONSTRAINT chk_approval_requests_status CHECK (status IN ('PENDING_APPROVAL', 'APPROVED', 'REJECTED'))
);

CREATE INDEX idx_approval_requests_status ON approval_requests (status);
CREATE INDEX idx_approval_requests_resource ON approval_requests (resource_id, action_type);
