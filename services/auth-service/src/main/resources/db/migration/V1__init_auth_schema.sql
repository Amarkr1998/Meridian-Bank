-- Meridian Bank — auth-service initial schema
-- Owns: users (credentials + RBAC role), refresh_tokens, login_attempts, password_reset_tokens.
-- This database (auth_service) is exclusive to auth-service — see docs/adr/0003-postgresql-system-of-record.md.

CREATE TABLE users (
    id                     UUID PRIMARY KEY,
    email                  VARCHAR(255)  NOT NULL,
    password_hash          VARCHAR(255)  NOT NULL,
    role                   VARCHAR(30)   NOT NULL,
    status                 VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    mfa_enabled            BOOLEAN       NOT NULL DEFAULT TRUE,
    failed_login_attempts  INTEGER       NOT NULL DEFAULT 0,
    locked_until           TIMESTAMPTZ,
    last_login_at          TIMESTAMPTZ,
    created_at             TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ   NOT NULL DEFAULT now(),
    version                BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uq_users_email CHECK (email = lower(email)),
    CONSTRAINT chk_users_role CHECK (role IN
        ('CUSTOMER', 'OPERATIONS', 'COMPLIANCE_OFFICER', 'RISK_ANALYST', 'AUDITOR', 'ADMIN')),
    CONSTRAINT chk_users_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'DISABLED')),
    CONSTRAINT chk_users_failed_attempts_non_negative CHECK (failed_login_attempts >= 0)
);

CREATE UNIQUE INDEX uq_users_email_idx ON users (email);
CREATE INDEX idx_users_role ON users (role);
CREATE INDEX idx_users_status ON users (status);

CREATE TABLE refresh_tokens (
    id               UUID PRIMARY KEY,
    user_id          UUID          NOT NULL REFERENCES users (id),
    token_hash       VARCHAR(255)  NOT NULL,
    issued_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    expires_at       TIMESTAMPTZ   NOT NULL,
    revoked          BOOLEAN       NOT NULL DEFAULT FALSE,
    replaced_by_id   UUID          REFERENCES refresh_tokens (id),
    created_by_ip    VARCHAR(64)
);

CREATE UNIQUE INDEX uq_refresh_tokens_token_hash_idx ON refresh_tokens (token_hash);
CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens (user_id);
CREATE INDEX idx_refresh_tokens_user_active ON refresh_tokens (user_id, revoked, expires_at);

CREATE TABLE login_attempts (
    id               UUID PRIMARY KEY,
    user_id          UUID          REFERENCES users (id),
    email_attempted  VARCHAR(255)  NOT NULL,
    successful       BOOLEAN       NOT NULL,
    failure_reason   VARCHAR(100),
    ip_address       VARCHAR(64),
    attempted_at     TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX idx_login_attempts_user_id ON login_attempts (user_id);
CREATE INDEX idx_login_attempts_attempted_at ON login_attempts (attempted_at);

CREATE TABLE password_reset_tokens (
    id           UUID PRIMARY KEY,
    user_id      UUID          NOT NULL REFERENCES users (id),
    token_hash   VARCHAR(255)  NOT NULL,
    expires_at   TIMESTAMPTZ   NOT NULL,
    used         BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_password_reset_tokens_hash_idx ON password_reset_tokens (token_hash);
CREATE INDEX idx_password_reset_tokens_user_id ON password_reset_tokens (user_id);
