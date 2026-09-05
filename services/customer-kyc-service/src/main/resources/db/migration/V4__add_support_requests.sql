-- Meridian Bank — customer support (Phase 13). See docs/database/domain-model.md ("Exact
-- ownership of support_requests will be finalized in Phase 13") — customer-kyc-service owns it:
-- a support request is fundamentally tied to the customer raising it (CUSTOMERS ||--o{
-- SUPPORT_REQUESTS : raises in the ERD), not to any particular account or transaction, and this
-- service already owns the Customer entity.
--
-- Single decision round only (OPEN -> IN_PROGRESS -> RESOLVED, no reopen cycle) — same documented
-- scope reduction as maker-checker's "no return for revision" (ADR-0011) and KYC's own
-- append-only-per-submission model; a customer unsatisfied with a RESOLVED ticket raises a new one
-- rather than reopening the old one.
CREATE TABLE support_requests (
    id                UUID PRIMARY KEY,
    customer_id       UUID          NOT NULL,
    category          VARCHAR(20)   NOT NULL,
    subject           VARCHAR(200)  NOT NULL,
    description       VARCHAR(2000) NOT NULL,
    status            VARCHAR(20)   NOT NULL DEFAULT 'OPEN',
    assigned_to       UUID,
    assigned_at       TIMESTAMPTZ,
    resolved_by       UUID,
    resolved_at       TIMESTAMPTZ,
    resolution_notes  VARCHAR(1000),
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT chk_support_requests_status CHECK (status IN ('OPEN', 'IN_PROGRESS', 'RESOLVED')),
    CONSTRAINT chk_support_requests_category
        CHECK (category IN ('ACCOUNT', 'PAYMENT', 'KYC', 'FRAUD', 'GENERAL'))
);

CREATE INDEX idx_support_requests_customer_id ON support_requests (customer_id);
CREATE INDEX idx_support_requests_status ON support_requests (status);
