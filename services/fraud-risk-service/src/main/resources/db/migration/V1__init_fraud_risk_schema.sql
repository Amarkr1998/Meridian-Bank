-- Meridian Bank — fraud-risk-service initial schema
-- Owns: fraud_rules, risk_assessments, fraud_alerts, aml_alerts.
-- customer_id / source_account_id / destination_account_id / transaction_id are opaque UUIDs
-- matching identities minted in other services — not cross-database foreign keys (see
-- docs/adr/0003-postgresql-system-of-record.md). fraud_rules rows are seeded by
-- FraudRuleBootstrapRunner at application startup (same idempotent pattern as auth-service's
-- AdminBootstrapRunner), not by this migration.

CREATE TABLE fraud_rules (
    id                       UUID PRIMARY KEY,
    rule_code                VARCHAR(50)   NOT NULL UNIQUE,
    category                 VARCHAR(10)   NOT NULL,
    description              VARCHAR(255)  NOT NULL,
    weight                   INT           NOT NULL,
    threshold_numeric        NUMERIC(18,2) NOT NULL,
    threshold_window_seconds INT,
    threshold_count          INT,
    enabled                  BOOLEAN       NOT NULL DEFAULT true,
    created_at               TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_by               UUID,
    CONSTRAINT chk_fraud_rules_category CHECK (category IN ('FRAUD', 'AML'))
);

-- Append-only: every risk-assessment call writes exactly one row here, and it is this service's
-- only source of transaction history for velocity/frequency/repeat-behavior rules — see
-- RiskAssessment's Javadoc.
CREATE TABLE risk_assessments (
    id                      UUID PRIMARY KEY,
    transaction_id          UUID          NOT NULL UNIQUE,
    customer_id             UUID          NOT NULL,
    source_account_id       UUID          NOT NULL,
    destination_account_id  UUID,
    amount                  NUMERIC(18,2) NOT NULL,
    currency                VARCHAR(3)    NOT NULL,
    score                   INT           NOT NULL,
    decision                VARCHAR(10)   NOT NULL,
    rule_hits               VARCHAR(500)  NOT NULL DEFAULT '',
    created_at              TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT chk_risk_assessments_decision CHECK (decision IN ('ALLOW', 'REVIEW', 'BLOCK')),
    CONSTRAINT chk_risk_assessments_amount_positive CHECK (amount > 0)
);

-- Supports the velocity/frequency/structuring/repeated-beneficiary queries in FraudRuleEngine and
-- AmlSignalEvaluator — always filtered by customer_id + a created_at lower bound.
CREATE INDEX idx_risk_assessments_customer_created ON risk_assessments (customer_id, created_at);
CREATE INDEX idx_risk_assessments_customer_destination ON risk_assessments (customer_id, destination_account_id);

CREATE TABLE fraud_alerts (
    id                      UUID PRIMARY KEY,
    transaction_id          UUID          NOT NULL,
    customer_id             UUID          NOT NULL,
    source_account_id       UUID          NOT NULL,
    destination_account_id  UUID,
    amount                  NUMERIC(18,2) NOT NULL,
    currency                VARCHAR(3)    NOT NULL,
    score                   INT           NOT NULL,
    decision                VARCHAR(10)   NOT NULL,
    rule_hits               VARCHAR(500)  NOT NULL DEFAULT '',
    status                  VARCHAR(20)   NOT NULL DEFAULT 'OPEN',
    created_at              TIMESTAMPTZ   NOT NULL DEFAULT now(),
    reviewed_at             TIMESTAMPTZ,
    reviewed_by             UUID,
    resolution_notes        VARCHAR(1000),
    CONSTRAINT chk_fraud_alerts_status CHECK (status IN ('OPEN', 'UNDER_REVIEW', 'CLEARED', 'ESCALATED', 'CONFIRMED_FRAUD'))
);

CREATE INDEX idx_fraud_alerts_status ON fraud_alerts (status);
CREATE INDEX idx_fraud_alerts_customer_id ON fraud_alerts (customer_id);

CREATE TABLE aml_alerts (
    id                UUID          PRIMARY KEY,
    transaction_id    UUID          NOT NULL,
    customer_id       UUID          NOT NULL,
    signal_code       VARCHAR(50)   NOT NULL,
    amount            NUMERIC(18,2) NOT NULL,
    currency          VARCHAR(3)    NOT NULL,
    status            VARCHAR(20)   NOT NULL DEFAULT 'OPEN',
    details           VARCHAR(500),
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    reviewed_at       TIMESTAMPTZ,
    reviewed_by       UUID,
    resolution_notes  VARCHAR(1000),
    CONSTRAINT chk_aml_alerts_status CHECK (status IN ('OPEN', 'UNDER_REVIEW', 'CLEARED', 'ESCALATED'))
);

CREATE INDEX idx_aml_alerts_status ON aml_alerts (status);
CREATE INDEX idx_aml_alerts_customer_id ON aml_alerts (customer_id);
