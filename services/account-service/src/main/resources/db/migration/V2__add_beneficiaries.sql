-- Meridian Bank — account-service: beneficiary management (Phase 5)
-- Owns: beneficiaries, beneficiary_status_history.
-- beneficiary_account_number references accounts.account_number in this same database (an
-- ordinary FK is not used since the referenced value is a natural key, not accounts.id, and a
-- deleted/renumbered account is not expected in this demo — see BeneficiaryService for the
-- application-level existence/ACTIVE check performed at add time).

CREATE TABLE beneficiaries (
    id                         UUID PRIMARY KEY,
    customer_id                UUID          NOT NULL,
    nickname                   VARCHAR(100)  NOT NULL,
    beneficiary_name           VARCHAR(200)  NOT NULL,
    beneficiary_account_number VARCHAR(20)   NOT NULL,
    status                     VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    activated_at               TIMESTAMPTZ,
    created_at                 TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at                 TIMESTAMPTZ   NOT NULL DEFAULT now(),
    version                    BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT chk_beneficiaries_status CHECK (status IN ('PENDING', 'ACTIVE', 'INACTIVE', 'BLOCKED'))
);

CREATE INDEX idx_beneficiaries_customer_id ON beneficiaries (customer_id);
CREATE INDEX idx_beneficiaries_status ON beneficiaries (status);
-- A customer cannot add the same recipient account twice while a prior entry still exists
-- (deletion frees the account number up again — see BeneficiaryService).
CREATE UNIQUE INDEX uq_beneficiaries_customer_account_idx ON beneficiaries (customer_id, beneficiary_account_number);

CREATE TABLE beneficiary_status_history (
    id               UUID PRIMARY KEY,
    beneficiary_id   UUID          NOT NULL REFERENCES beneficiaries (id),
    old_status       VARCHAR(20)   NOT NULL,
    new_status       VARCHAR(20)   NOT NULL,
    reason           VARCHAR(500),
    changed_by       UUID          NOT NULL,
    changed_at       TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX idx_beneficiary_status_history_beneficiary_id ON beneficiary_status_history (beneficiary_id);
