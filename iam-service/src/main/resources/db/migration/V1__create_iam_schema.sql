-- V1__create_iam_schema.sql
-- Flyway migration — runs once, never modified after release
-- To change schema: create V2__your_change.sql

CREATE SCHEMA IF NOT EXISTS iam;

-- ──────────────────────────────────────────────────────────────────────────
-- USERS (Aggregate Root)
-- ──────────────────────────────────────────────────────────────────────────
CREATE TABLE iam.users (
    user_id          UUID         PRIMARY KEY  DEFAULT gen_random_uuid(),
    email            VARCHAR(254) NOT NULL,
    phone_number     VARCHAR(20),
    status           VARCHAR(30)  NOT NULL DEFAULT 'PENDING_VERIFICATION'
                        CHECK (status IN (
                            'PENDING_VERIFICATION','ACTIVE',
                            'SUSPENDED','LOCKED','DELETED')),
    mfa_enabled      BOOLEAN      NOT NULL DEFAULT FALSE,
    mfa_secret       TEXT,
    email_verified   BOOLEAN      NOT NULL DEFAULT FALSE,
    phone_verified   BOOLEAN      NOT NULL DEFAULT FALSE,
    failed_attempts  SMALLINT     NOT NULL DEFAULT 0,
    locked_until     TIMESTAMPTZ,
    last_login_at    TIMESTAMPTZ,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version          BIGINT       NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uq_users_email ON iam.users (LOWER(email));
CREATE INDEX idx_users_status     ON iam.users (status);

-- ──────────────────────────────────────────────────────────────────────────
-- CREDENTIALS
-- ──────────────────────────────────────────────────────────────────────────
CREATE TABLE iam.credentials (
    credential_id    UUID         PRIMARY KEY  DEFAULT gen_random_uuid(),
    user_id          UUID         NOT NULL REFERENCES iam.users(user_id) ON DELETE CASCADE,
    password_hash    TEXT         NOT NULL,
    algorithm        VARCHAR(20)  NOT NULL DEFAULT 'BCRYPT',
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    expires_at       TIMESTAMPTZ,
    is_active        BOOLEAN      NOT NULL DEFAULT TRUE
);

CREATE INDEX idx_credentials_user ON iam.credentials (user_id, is_active);

-- ──────────────────────────────────────────────────────────────────────────
-- ROLES & PERMISSIONS (RBAC)
-- ──────────────────────────────────────────────────────────────────────────
CREATE TABLE iam.roles (
    role_id          UUID         PRIMARY KEY  DEFAULT gen_random_uuid(),
    name             VARCHAR(50)  NOT NULL UNIQUE,
    description      TEXT,
    is_system_role   BOOLEAN      NOT NULL DEFAULT FALSE
);

CREATE TABLE iam.permissions (
    permission_id    UUID         PRIMARY KEY  DEFAULT gen_random_uuid(),
    resource         VARCHAR(100) NOT NULL,
    action           VARCHAR(50)  NOT NULL,
    UNIQUE (resource, action)
);

CREATE TABLE iam.role_permissions (
    role_id          UUID NOT NULL REFERENCES iam.roles(role_id),
    permission_id    UUID NOT NULL REFERENCES iam.permissions(permission_id),
    PRIMARY KEY (role_id, permission_id)
);

CREATE TABLE iam.user_roles (
    user_id          UUID NOT NULL REFERENCES iam.users(user_id),
    role_id          UUID NOT NULL REFERENCES iam.roles(role_id),
    assigned_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    assigned_by      UUID,
    PRIMARY KEY (user_id, role_id)
);

-- ──────────────────────────────────────────────────────────────────────────
-- SESSIONS (refresh token tracking)
-- ──────────────────────────────────────────────────────────────────────────
CREATE TABLE iam.sessions (
    session_id           UUID         PRIMARY KEY  DEFAULT gen_random_uuid(),
    user_id              UUID         NOT NULL REFERENCES iam.users(user_id),
    refresh_token_hash   TEXT         NOT NULL UNIQUE,
    device_fingerprint   TEXT,
    ip_address           INET,
    user_agent           TEXT,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    expires_at           TIMESTAMPTZ  NOT NULL,
    revoked_at           TIMESTAMPTZ
);

CREATE INDEX idx_sessions_user    ON iam.sessions (user_id);
CREATE INDEX idx_sessions_expires ON iam.sessions (expires_at)
    WHERE revoked_at IS NULL;

-- ──────────────────────────────────────────────────────────────────────────
-- OUTBOX (Transactional Outbox pattern)
-- ──────────────────────────────────────────────────────────────────────────
CREATE TABLE iam.outbox_events (
    event_id         UUID         PRIMARY KEY  DEFAULT gen_random_uuid(),
    aggregate_type   VARCHAR(50)  NOT NULL,
    aggregate_id     UUID         NOT NULL,
    event_type       VARCHAR(100) NOT NULL,
    payload          JSONB        NOT NULL,
    status           VARCHAR(20)  NOT NULL DEFAULT 'PENDING'
                        CHECK (status IN ('PENDING','PUBLISHED','FAILED')),
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    published_at     TIMESTAMPTZ
);

CREATE INDEX idx_outbox_pending ON iam.outbox_events (created_at)
    WHERE status = 'PENDING';

-- ──────────────────────────────────────────────────────────────────────────
-- SEED DATA — system roles
-- ──────────────────────────────────────────────────────────────────────────
INSERT INTO iam.roles (name, description, is_system_role) VALUES
    ('CUSTOMER',           'Standard banking customer',                TRUE),
    ('TELLER',             'Bank teller — assisted transactions',      TRUE),
    ('COMPLIANCE_OFFICER', 'KYC/AML review and approval',             TRUE),
    ('ADMIN',              'System administrator',                     TRUE);

INSERT INTO iam.permissions (resource, action) VALUES
    ('account',     'read'),
    ('account',     'write'),
    ('transaction', 'read'),
    ('transaction', 'write'),
    ('kyc',         'read'),
    ('kyc',         'approve'),
    ('aml',         'read'),
    ('aml',         'investigate'),
    ('user',        'read'),
    ('user',        'admin');
