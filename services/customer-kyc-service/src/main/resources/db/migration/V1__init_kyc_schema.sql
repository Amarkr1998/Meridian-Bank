-- Meridian Bank — customer-kyc-service initial schema
-- Owns: customers, customer_status_history, kyc_records, customer_documents.
-- customer.id is shared with the identity created in auth-service's `users` table at
-- registration time (see AuthServiceClient) — not a foreign key across databases
-- (see docs/adr/0003-postgresql-system-of-record.md), just a matching UUID.

CREATE TABLE customers (
    id                UUID PRIMARY KEY,
    email             VARCHAR(255)  NOT NULL,
    first_name        VARCHAR(100)  NOT NULL,
    last_name         VARCHAR(100)  NOT NULL,
    date_of_birth     DATE          NOT NULL,
    phone             VARCHAR(30)   NOT NULL,
    address_line1     VARCHAR(200)  NOT NULL,
    address_line2     VARCHAR(200),
    city              VARCHAR(100)  NOT NULL,
    state             VARCHAR(100)  NOT NULL,
    postal_code       VARCHAR(20)   NOT NULL,
    country           VARCHAR(100)  NOT NULL,
    status            VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    contact_verified  BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    version           BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT chk_customers_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'BLOCKED', 'SUSPENDED'))
);

CREATE UNIQUE INDEX uq_customers_email_idx ON customers (email);
CREATE INDEX idx_customers_status ON customers (status);
CREATE INDEX idx_customers_last_name ON customers (last_name);

CREATE TABLE customer_status_history (
    id             UUID PRIMARY KEY,
    customer_id    UUID          NOT NULL REFERENCES customers (id),
    old_status     VARCHAR(20)   NOT NULL,
    new_status     VARCHAR(20)   NOT NULL,
    reason         VARCHAR(500),
    changed_by     UUID          NOT NULL,
    changed_at     TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX idx_customer_status_history_customer_id ON customer_status_history (customer_id);

-- Append-only: a rejected customer resubmits by creating a NEW row, never overwriting one in
-- place, so the full review history is just "all records for this customer" — see
-- docs/architecture/kyc-flow.md.
CREATE TABLE kyc_records (
    id                UUID PRIMARY KEY,
    customer_id       UUID          NOT NULL REFERENCES customers (id),
    status            VARCHAR(20)   NOT NULL DEFAULT 'KYC_PENDING',
    nationality       VARCHAR(100)  NOT NULL,
    occupation        VARCHAR(150)  NOT NULL,
    submitted_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    reviewed_at       TIMESTAMPTZ,
    reviewed_by       UUID,
    rejection_reason  VARCHAR(500),
    CONSTRAINT chk_kyc_records_status CHECK (
        status IN ('KYC_PENDING', 'KYC_IN_REVIEW', 'KYC_VERIFIED', 'KYC_REJECTED')
    )
);

CREATE INDEX idx_kyc_records_customer_id ON kyc_records (customer_id);
CREATE INDEX idx_kyc_records_status ON kyc_records (status);

-- Metadata only — never a real identity document. See docs/governance/governance-principles.md.
CREATE TABLE customer_documents (
    id                   UUID PRIMARY KEY,
    kyc_record_id        UUID          NOT NULL REFERENCES kyc_records (id),
    document_type        VARCHAR(30)   NOT NULL,
    document_reference   VARCHAR(100)  NOT NULL,
    uploaded_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT chk_customer_documents_type CHECK (
        document_type IN ('NATIONAL_ID', 'PASSPORT', 'DRIVERS_LICENSE', 'PROOF_OF_ADDRESS')
    )
);

CREATE INDEX idx_customer_documents_kyc_record_id ON customer_documents (kyc_record_id);
