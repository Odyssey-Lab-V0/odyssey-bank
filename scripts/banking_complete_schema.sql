-- =============================================================
-- DIGITAL BANKING PLATFORM — COMPLETE DATABASE SCHEMA
-- PostgreSQL 14+ / Aurora PostgreSQL compatible
-- Run order: execute this file top to bottom once
-- =============================================================

-- =============================================================
-- 0. SETUP — Create all schemas
-- =============================================================

CREATE SCHEMA IF NOT EXISTS iam;
CREATE SCHEMA IF NOT EXISTS onboarding;
CREATE SCHEMA IF NOT EXISTS banking;
CREATE SCHEMA IF NOT EXISTS kyc_aml;
CREATE SCHEMA IF NOT EXISTS notification;
CREATE SCHEMA IF NOT EXISTS audit;


-- =============================================================
-- 1. IAM SCHEMA — Identity & Access Management
-- =============================================================

-- ------------------------------------------------------------
-- 1.1 USERS (Aggregate Root)
-- ------------------------------------------------------------
CREATE TABLE iam.users (
    user_id          UUID         PRIMARY KEY  DEFAULT gen_random_uuid(),
    email            VARCHAR(254) NOT NULL,
    phone_number     VARCHAR(20),
    status           VARCHAR(30)  NOT NULL DEFAULT 'PENDING_VERIFICATION'
                        CHECK (status IN (
                            'PENDING_VERIFICATION',
                            'ACTIVE',
                            'SUSPENDED',
                            'LOCKED',
                            'DELETED'
                        )),
    mfa_enabled      BOOLEAN      NOT NULL DEFAULT FALSE,
    mfa_secret       TEXT,
    email_verified   BOOLEAN      NOT NULL DEFAULT FALSE,
    phone_verified   BOOLEAN      NOT NULL DEFAULT FALSE,
    failed_attempts  SMALLINT     NOT NULL DEFAULT 0
                        CHECK (failed_attempts >= 0),
    locked_until     TIMESTAMPTZ,
    last_login_at    TIMESTAMPTZ,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version          BIGINT       NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uq_users_email  ON iam.users (LOWER(email));
CREATE INDEX idx_users_status       ON iam.users (status);
CREATE INDEX idx_users_locked_until ON iam.users (locked_until)
    WHERE locked_until IS NOT NULL;

-- ------------------------------------------------------------
-- 1.2 CREDENTIALS
-- ------------------------------------------------------------
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

-- ------------------------------------------------------------
-- 1.3 ROLES
-- ------------------------------------------------------------
CREATE TABLE iam.roles (
    role_id        UUID        PRIMARY KEY  DEFAULT gen_random_uuid(),
    name           VARCHAR(50) NOT NULL UNIQUE,
    description    TEXT,
    is_system_role BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ------------------------------------------------------------
-- 1.4 PERMISSIONS
-- ------------------------------------------------------------
CREATE TABLE iam.permissions (
    permission_id UUID         PRIMARY KEY  DEFAULT gen_random_uuid(),
    resource      VARCHAR(100) NOT NULL,
    action        VARCHAR(50)  NOT NULL,
    description   TEXT,
    UNIQUE (resource, action)
);

-- ------------------------------------------------------------
-- 1.5 ROLE <-> PERMISSIONS (M:N)
-- ------------------------------------------------------------
CREATE TABLE iam.role_permissions (
    role_id       UUID NOT NULL REFERENCES iam.roles(role_id)       ON DELETE CASCADE,
    permission_id UUID NOT NULL REFERENCES iam.permissions(permission_id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_id)
);

-- ------------------------------------------------------------
-- 1.6 USER <-> ROLES (M:N)
-- ------------------------------------------------------------
CREATE TABLE iam.user_roles (
    user_id     UUID NOT NULL REFERENCES iam.users(user_id) ON DELETE CASCADE,
    role_id     UUID NOT NULL REFERENCES iam.roles(role_id) ON DELETE CASCADE,
    assigned_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    assigned_by UUID,
    PRIMARY KEY (user_id, role_id)
);

CREATE INDEX idx_user_roles_user ON iam.user_roles (user_id);

-- ------------------------------------------------------------
-- 1.7 SESSIONS (refresh token tracking)
-- ------------------------------------------------------------
CREATE TABLE iam.sessions (
    session_id          UUID        PRIMARY KEY  DEFAULT gen_random_uuid(),
    user_id             UUID        NOT NULL REFERENCES iam.users(user_id) ON DELETE CASCADE,
    refresh_token_hash  TEXT        NOT NULL UNIQUE,
    device_fingerprint  TEXT,
    ip_address          INET,
    user_agent          TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at          TIMESTAMPTZ NOT NULL,
    revoked_at          TIMESTAMPTZ
);

CREATE INDEX idx_sessions_user    ON iam.sessions (user_id);
CREATE INDEX idx_sessions_expires ON iam.sessions (expires_at)
    WHERE revoked_at IS NULL;

-- ------------------------------------------------------------
-- 1.8 EMAIL VERIFICATION TOKENS
-- ------------------------------------------------------------
CREATE TABLE iam.verification_tokens (
    token_id    UUID        PRIMARY KEY  DEFAULT gen_random_uuid(),
    user_id     UUID        NOT NULL REFERENCES iam.users(user_id) ON DELETE CASCADE,
    token_hash  TEXT        NOT NULL UNIQUE,
    token_type  VARCHAR(20) NOT NULL
                    CHECK (token_type IN ('EMAIL_VERIFY', 'PHONE_VERIFY', 'PASSWORD_RESET')),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at  TIMESTAMPTZ NOT NULL DEFAULT now() + INTERVAL '24 hours',
    used_at     TIMESTAMPTZ
);

CREATE INDEX idx_verif_user    ON iam.verification_tokens (user_id);
CREATE INDEX idx_verif_expires ON iam.verification_tokens (expires_at)
    WHERE used_at IS NULL;

-- ------------------------------------------------------------
-- 1.9 OUTBOX EVENTS (Transactional Outbox pattern)
-- ------------------------------------------------------------
CREATE TABLE iam.outbox_events (
    event_id       UUID         PRIMARY KEY  DEFAULT gen_random_uuid(),
    aggregate_type VARCHAR(50)  NOT NULL,
    aggregate_id   UUID         NOT NULL,
    event_type     VARCHAR(100) NOT NULL,
    payload        JSONB        NOT NULL,
    status         VARCHAR(20)  NOT NULL DEFAULT 'PENDING'
                       CHECK (status IN ('PENDING', 'PUBLISHED', 'FAILED')),
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    published_at   TIMESTAMPTZ,
    retry_count    SMALLINT     NOT NULL DEFAULT 0
);

CREATE INDEX idx_iam_outbox_pending ON iam.outbox_events (created_at ASC)
    WHERE status = 'PENDING';

-- ------------------------------------------------------------
-- 1.10 SEED DATA
-- ------------------------------------------------------------
INSERT INTO iam.roles (name, description, is_system_role) VALUES
    ('CUSTOMER',           'Standard banking customer — self-service only',          TRUE),
    ('TELLER',             'Bank teller — can assist with transactions',             TRUE),
    ('COMPLIANCE_OFFICER', 'KYC/AML review, approval, and SAR filing',              TRUE),
    ('ADMIN',              'System administrator — full access',                     TRUE),
    ('AUDITOR',            'Read-only access to audit logs and reports',             TRUE);

INSERT INTO iam.permissions (resource, action, description) VALUES
    ('account',     'read',        'View own account details and balance'),
    ('account',     'write',       'Create and modify accounts'),
    ('account',     'freeze',      'Freeze or unfreeze an account'),
    ('transaction', 'read',        'View transaction history'),
    ('transaction', 'write',       'Initiate transfers and payments'),
    ('transaction', 'reverse',     'Reverse a posted transaction'),
    ('kyc',         'read',        'View KYC profiles'),
    ('kyc',         'approve',     'Approve or reject KYC applications'),
    ('aml',         'read',        'View AML alerts'),
    ('aml',         'investigate', 'Assign and work AML alerts'),
    ('aml',         'file_sar',    'File Suspicious Activity Reports'),
    ('user',        'read',        'View user profiles'),
    ('user',        'admin',       'Create, suspend, delete users'),
    ('audit',       'read',        'View audit logs');


-- =============================================================
-- 2. ONBOARDING SCHEMA — Customer Onboarding
-- =============================================================

-- ------------------------------------------------------------
-- 2.1 CUSTOMERS (Aggregate Root)
-- ------------------------------------------------------------
CREATE TABLE onboarding.customers (
    customer_id        UUID         PRIMARY KEY  DEFAULT gen_random_uuid(),
    user_id            UUID         NOT NULL UNIQUE,    -- logical ref to iam.users (no DB FK)
    first_name         VARCHAR(100) NOT NULL,
    middle_name        VARCHAR(100),
    last_name          VARCHAR(100) NOT NULL,
    date_of_birth      DATE         NOT NULL,
    gender             VARCHAR(20)  CHECK (gender IN ('MALE', 'FEMALE', 'NON_BINARY', 'PREFER_NOT_TO_SAY')),
    nationality        CHAR(2)      NOT NULL,           -- ISO 3166-1 alpha-2
    national_id_type   VARCHAR(30)  NOT NULL
                           CHECK (national_id_type IN (
                               'PASSPORT', 'NATIONAL_ID', 'DRIVER_LICENSE', 'RESIDENCE_PERMIT'
                           )),
    national_id_number VARCHAR(50)  NOT NULL,
    national_id_expiry DATE,
    tier               VARCHAR(20)  NOT NULL DEFAULT 'STANDARD'
                           CHECK (tier IN ('STANDARD', 'PREMIUM', 'VIP')),
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version            BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT chk_customer_age
        CHECK (date_of_birth <= CURRENT_DATE - INTERVAL '18 years')
);

CREATE INDEX idx_customers_user ON onboarding.customers (user_id);

-- ------------------------------------------------------------
-- 2.2 CUSTOMER ADDRESSES
-- ------------------------------------------------------------
CREATE TABLE onboarding.customer_addresses (
    address_id     UUID         PRIMARY KEY  DEFAULT gen_random_uuid(),
    customer_id    UUID         NOT NULL REFERENCES onboarding.customers(customer_id) ON DELETE CASCADE,
    address_type   VARCHAR(20)  NOT NULL CHECK (address_type IN ('RESIDENTIAL', 'MAILING')),
    line1          VARCHAR(200) NOT NULL,
    line2          VARCHAR(200),
    city           VARCHAR(100) NOT NULL,
    state_province VARCHAR(100),
    postal_code    VARCHAR(20)  NOT NULL,
    country        CHAR(2)      NOT NULL,               -- ISO 3166-1 alpha-2
    is_primary     BOOLEAN      NOT NULL DEFAULT FALSE,
    verified       BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_addresses_customer ON onboarding.customer_addresses (customer_id);

-- ------------------------------------------------------------
-- 2.3 CUSTOMER CONTACT DETAILS
-- ------------------------------------------------------------
CREATE TABLE onboarding.customer_contacts (
    contact_id   UUID        PRIMARY KEY  DEFAULT gen_random_uuid(),
    customer_id  UUID        NOT NULL REFERENCES onboarding.customers(customer_id) ON DELETE CASCADE,
    contact_type VARCHAR(20) NOT NULL CHECK (contact_type IN ('EMAIL', 'PHONE', 'ALTERNATE_PHONE')),
    value        VARCHAR(254) NOT NULL,
    is_primary   BOOLEAN     NOT NULL DEFAULT FALSE,
    verified     BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_contacts_customer ON onboarding.customer_contacts (customer_id);

-- ------------------------------------------------------------
-- 2.4 ONBOARDING APPLICATIONS (Aggregate Root)
-- ------------------------------------------------------------
CREATE TABLE onboarding.applications (
    application_id   UUID        PRIMARY KEY  DEFAULT gen_random_uuid(),
    customer_id      UUID        NOT NULL REFERENCES onboarding.customers(customer_id),
    status           VARCHAR(40) NOT NULL DEFAULT 'INITIATED'
                         CHECK (status IN (
                             'INITIATED',
                             'PERSONAL_INFO_SUBMITTED',
                             'DOCUMENTS_UPLOADED',
                             'KYC_PENDING',
                             'KYC_APPROVED',
                             'ACCOUNT_OPENED',
                             'COMPLETED',
                             'REJECTED',
                             'EXPIRED'
                         )),
    rejection_reason TEXT,
    kyc_reference    UUID,                              -- logical ref to kyc_aml.kyc_profiles
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    submitted_at     TIMESTAMPTZ,
    completed_at     TIMESTAMPTZ,
    expires_at       TIMESTAMPTZ NOT NULL DEFAULT now() + INTERVAL '90 days',
    version          BIGINT      NOT NULL DEFAULT 0
);

CREATE INDEX idx_applications_customer ON onboarding.applications (customer_id);
CREATE INDEX idx_applications_status   ON onboarding.applications (status);
CREATE INDEX idx_applications_expires  ON onboarding.applications (expires_at)
    WHERE status NOT IN ('COMPLETED', 'REJECTED', 'EXPIRED');

-- ------------------------------------------------------------
-- 2.5 DOCUMENTS
-- ------------------------------------------------------------
CREATE TABLE onboarding.documents (
    document_id     UUID        PRIMARY KEY  DEFAULT gen_random_uuid(),
    application_id  UUID        NOT NULL REFERENCES onboarding.applications(application_id),
    document_type   VARCHAR(50) NOT NULL
                        CHECK (document_type IN (
                            'PASSPORT_FRONT',
                            'PASSPORT_BACK',
                            'NATIONAL_ID_FRONT',
                            'NATIONAL_ID_BACK',
                            'DRIVER_LICENSE_FRONT',
                            'DRIVER_LICENSE_BACK',
                            'SELFIE',
                            'SELFIE_WITH_DOCUMENT',
                            'UTILITY_BILL',
                            'BANK_STATEMENT',
                            'OTHER'
                        )),
    s3_bucket       VARCHAR(100) NOT NULL,
    s3_key          VARCHAR(500) NOT NULL,
    content_type    VARCHAR(50)  NOT NULL,
    size_bytes      BIGINT       NOT NULL CHECK (size_bytes > 0),
    checksum_sha256 CHAR(64)     NOT NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'UPLOADED'
                        CHECK (status IN ('UPLOADED', 'UNDER_REVIEW', 'ACCEPTED', 'REJECTED')),
    rejection_reason TEXT,
    uploaded_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    reviewed_at     TIMESTAMPTZ
);

CREATE INDEX idx_docs_application ON onboarding.documents (application_id);
CREATE INDEX idx_docs_status      ON onboarding.documents (status);

-- ------------------------------------------------------------
-- 2.6 OUTBOX EVENTS
-- ------------------------------------------------------------
CREATE TABLE onboarding.outbox_events (
    event_id       UUID         PRIMARY KEY  DEFAULT gen_random_uuid(),
    aggregate_type VARCHAR(50)  NOT NULL,
    aggregate_id   UUID         NOT NULL,
    event_type     VARCHAR(100) NOT NULL,
    payload        JSONB        NOT NULL,
    status         VARCHAR(20)  NOT NULL DEFAULT 'PENDING'
                       CHECK (status IN ('PENDING', 'PUBLISHED', 'FAILED')),
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    published_at   TIMESTAMPTZ,
    retry_count    SMALLINT     NOT NULL DEFAULT 0
);

CREATE INDEX idx_onboarding_outbox_pending ON onboarding.outbox_events (created_at ASC)
    WHERE status = 'PENDING';


-- =============================================================
-- 3. BANKING SCHEMA — Core Banking (Accounts + Ledger)
-- =============================================================

-- ------------------------------------------------------------
-- 3.1 ACCOUNTS (Aggregate Root)
-- ------------------------------------------------------------
CREATE TABLE banking.accounts (
    account_id      UUID          PRIMARY KEY  DEFAULT gen_random_uuid(),
    customer_id     UUID          NOT NULL,              -- logical ref to onboarding.customers
    account_number  VARCHAR(34)   NOT NULL UNIQUE,       -- IBAN format
    account_type    VARCHAR(20)   NOT NULL
                        CHECK (account_type IN (
                            'CURRENT',
                            'SAVINGS',
                            'FIXED_DEPOSIT',
                            'INTERNAL'                   -- for fee buckets, suspense accounts
                        )),
    currency        CHAR(3)       NOT NULL DEFAULT 'USD', -- ISO 4217
    status          VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE'
                        CHECK (status IN ('ACTIVE', 'FROZEN', 'DORMANT', 'CLOSED')),
    overdraft_limit NUMERIC(18,2) NOT NULL DEFAULT 0.00 CHECK (overdraft_limit >= 0),
    interest_rate   NUMERIC(7,4),                        -- annual rate e.g. 0.0350 = 3.5%
    maturity_date   DATE,                                -- for FIXED_DEPOSIT accounts only
    opened_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    closed_at       TIMESTAMPTZ,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    version         BIGINT        NOT NULL DEFAULT 0
);

CREATE INDEX idx_accounts_customer ON banking.accounts (customer_id);
CREATE INDEX idx_accounts_number   ON banking.accounts (account_number);
CREATE INDEX idx_accounts_status   ON banking.accounts (status);

-- ------------------------------------------------------------
-- 3.2 ACCOUNT LIMITS
-- ------------------------------------------------------------
CREATE TABLE banking.account_limits (
    limit_id    UUID          PRIMARY KEY  DEFAULT gen_random_uuid(),
    account_id  UUID          NOT NULL REFERENCES banking.accounts(account_id) ON DELETE CASCADE,
    limit_type  VARCHAR(40)   NOT NULL
                    CHECK (limit_type IN (
                        'DAILY_DEBIT',
                        'DAILY_CREDIT',
                        'SINGLE_TRANSACTION',
                        'MONTHLY_DEBIT',
                        'INTERNATIONAL_TRANSFER'
                    )),
    amount      NUMERIC(18,2) NOT NULL CHECK (amount > 0),
    currency    CHAR(3)       NOT NULL,
    period      VARCHAR(20)   NOT NULL CHECK (period IN ('PER_TXN', 'DAILY', 'WEEKLY', 'MONTHLY')),
    UNIQUE (account_id, limit_type)
);

-- ------------------------------------------------------------
-- 3.3 TRANSACTIONS (Aggregate Root)
-- ------------------------------------------------------------
CREATE TABLE banking.transactions (
    transaction_id   UUID          PRIMARY KEY  DEFAULT gen_random_uuid(),
    idempotency_key  UUID          NOT NULL UNIQUE,      -- client-supplied, prevents duplicates on retry
    reference_number VARCHAR(50)   NOT NULL UNIQUE,      -- human-readable e.g. TXN-20240613-00001
    transaction_type VARCHAR(30)   NOT NULL
                         CHECK (transaction_type IN (
                             'CREDIT',
                             'DEBIT',
                             'TRANSFER',
                             'FEE',
                             'INTEREST',
                             'REVERSAL',
                             'ADJUSTMENT'
                         )),
    amount           NUMERIC(18,2) NOT NULL CHECK (amount > 0),
    currency         CHAR(3)       NOT NULL,
    status           VARCHAR(20)   NOT NULL DEFAULT 'PENDING'
                         CHECK (status IN ('PENDING', 'POSTED', 'FAILED', 'REVERSED')),
    description      VARCHAR(500),
    channel          VARCHAR(30)
                         CHECK (channel IN ('MOBILE', 'WEB', 'ATM', 'BRANCH', 'API', 'INTERNAL', 'SYSTEM')),
    initiated_by     UUID,                               -- user_id of the person who initiated
    reversal_of      UUID          REFERENCES banking.transactions(transaction_id),
    metadata         JSONB,                              -- extensible: IP address, device ID, geo location
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    posted_at        TIMESTAMPTZ,
    failed_reason    TEXT
);

CREATE INDEX idx_transactions_idempotency ON banking.transactions (idempotency_key);
CREATE INDEX idx_transactions_status      ON banking.transactions (status);
CREATE INDEX idx_transactions_created     ON banking.transactions (created_at DESC);
CREATE INDEX idx_transactions_reference   ON banking.transactions (reference_number);

-- ------------------------------------------------------------
-- 3.4 LEDGER ENTRIES — double-entry bookkeeping core
-- Every transaction MUST produce exactly 2+ entries summing to 0
-- DEBIT increases liability/equity; CREDIT increases asset
-- ------------------------------------------------------------
CREATE TABLE banking.ledger_entries (
    entry_id        UUID          PRIMARY KEY  DEFAULT gen_random_uuid(),
    transaction_id  UUID          NOT NULL REFERENCES banking.transactions(transaction_id),
    account_id      UUID          NOT NULL REFERENCES banking.accounts(account_id),
    entry_type      VARCHAR(10)   NOT NULL CHECK (entry_type IN ('DEBIT', 'CREDIT')),
    amount          NUMERIC(18,2) NOT NULL CHECK (amount > 0),
    currency        CHAR(3)       NOT NULL,
    running_balance NUMERIC(18,2) NOT NULL,              -- denormalized snapshot for statement generation
    value_date      DATE          NOT NULL DEFAULT CURRENT_DATE,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX idx_ledger_account    ON banking.ledger_entries (account_id, created_at DESC);
CREATE INDEX idx_ledger_txn        ON banking.ledger_entries (transaction_id);
CREATE INDEX idx_ledger_value_date ON banking.ledger_entries (account_id, value_date DESC);

-- ------------------------------------------------------------
-- 3.5 HOLDS — pending authorizations reducing available balance
-- ------------------------------------------------------------
CREATE TABLE banking.holds (
    hold_id     UUID          PRIMARY KEY  DEFAULT gen_random_uuid(),
    account_id  UUID          NOT NULL REFERENCES banking.accounts(account_id),
    amount      NUMERIC(18,2) NOT NULL CHECK (amount > 0),
    currency    CHAR(3)       NOT NULL,
    reason      VARCHAR(200),
    reference   VARCHAR(100),
    expires_at  TIMESTAMPTZ   NOT NULL,
    released_at TIMESTAMPTZ,
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX idx_holds_active ON banking.holds (account_id, expires_at)
    WHERE released_at IS NULL;

-- ------------------------------------------------------------
-- 3.6 PAYMENT ORDERS — pre-posting intent (optional workflow)
-- ------------------------------------------------------------
CREATE TABLE banking.payment_orders (
    order_id          UUID          PRIMARY KEY  DEFAULT gen_random_uuid(),
    idempotency_key   UUID          NOT NULL UNIQUE,
    from_account_id   UUID          NOT NULL REFERENCES banking.accounts(account_id),
    to_account_id     UUID,                              -- NULL for external transfers
    to_account_number VARCHAR(34),                       -- for external
    amount            NUMERIC(18,2) NOT NULL CHECK (amount > 0),
    currency          CHAR(3)       NOT NULL,
    payment_type      VARCHAR(30)   NOT NULL
                          CHECK (payment_type IN (
                              'INTERNAL_TRANSFER',
                              'EXTERNAL_WIRE',
                              'BILL_PAYMENT',
                              'DIRECT_DEBIT',
                              'STANDING_ORDER'
                          )),
    scheduled_date    DATE,
    description       VARCHAR(500),
    status            VARCHAR(20)   NOT NULL DEFAULT 'PENDING'
                          CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED', 'CANCELLED')),
    transaction_id    UUID          REFERENCES banking.transactions(transaction_id),
    initiated_by      UUID          NOT NULL,
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    processed_at      TIMESTAMPTZ
);

CREATE INDEX idx_payment_orders_account ON banking.payment_orders (from_account_id, created_at DESC);
CREATE INDEX idx_payment_orders_status  ON banking.payment_orders (status)
    WHERE status IN ('PENDING', 'PROCESSING');

-- ------------------------------------------------------------
-- 3.7 ACCOUNT BALANCE VIEW — derived, never stored
-- Available balance = posted ledger total - active holds
-- ------------------------------------------------------------
CREATE VIEW banking.account_balances AS
SELECT
    a.account_id,
    a.account_number,
    a.customer_id,
    a.account_type,
    a.currency,
    a.status,
    a.overdraft_limit,
    COALESCE(
        SUM(CASE
            WHEN le.entry_type = 'CREDIT' THEN  le.amount
            WHEN le.entry_type = 'DEBIT'  THEN -le.amount
        END), 0
    )                                           AS ledger_balance,
    COALESCE(SUM(h.amount), 0)                  AS pending_holds,
    COALESCE(
        SUM(CASE
            WHEN le.entry_type = 'CREDIT' THEN  le.amount
            WHEN le.entry_type = 'DEBIT'  THEN -le.amount
        END), 0
    ) - COALESCE(SUM(h.amount), 0)              AS available_balance
FROM banking.accounts a
LEFT JOIN banking.ledger_entries le ON le.account_id = a.account_id
LEFT JOIN banking.holds h
    ON  h.account_id  = a.account_id
    AND h.released_at IS NULL
    AND h.expires_at  > now()
GROUP BY
    a.account_id,
    a.account_number,
    a.customer_id,
    a.account_type,
    a.currency,
    a.status,
    a.overdraft_limit;

-- ------------------------------------------------------------
-- 3.8 OUTBOX EVENTS
-- ------------------------------------------------------------
CREATE TABLE banking.outbox_events (
    event_id       UUID         PRIMARY KEY  DEFAULT gen_random_uuid(),
    aggregate_type VARCHAR(50)  NOT NULL,
    aggregate_id   UUID         NOT NULL,
    event_type     VARCHAR(100) NOT NULL,
    payload        JSONB        NOT NULL,
    status         VARCHAR(20)  NOT NULL DEFAULT 'PENDING'
                       CHECK (status IN ('PENDING', 'PUBLISHED', 'FAILED')),
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    published_at   TIMESTAMPTZ,
    retry_count    SMALLINT     NOT NULL DEFAULT 0
);

CREATE INDEX idx_banking_outbox_pending ON banking.outbox_events (created_at ASC)
    WHERE status = 'PENDING';

-- ------------------------------------------------------------
-- 3.9 SEED — Internal suspense accounts (required for double-entry)
-- ------------------------------------------------------------
INSERT INTO banking.accounts
    (account_id, customer_id, account_number, account_type, currency, status)
VALUES
    (gen_random_uuid(), '00000000-0000-0000-0000-000000000001',
     'INT-FEE-USD-001',  'INTERNAL', 'USD', 'ACTIVE'),
    (gen_random_uuid(), '00000000-0000-0000-0000-000000000001',
     'INT-SUSPENSE-001', 'INTERNAL', 'USD', 'ACTIVE'),
    (gen_random_uuid(), '00000000-0000-0000-0000-000000000001',
     'INT-INTEREST-001', 'INTERNAL', 'USD', 'ACTIVE');


-- =============================================================
-- 4. KYC_AML SCHEMA — Compliance
-- =============================================================

-- ------------------------------------------------------------
-- 4.1 KYC PROFILES (Aggregate Root)
-- ------------------------------------------------------------
CREATE TABLE kyc_aml.kyc_profiles (
    kyc_id              UUID        PRIMARY KEY  DEFAULT gen_random_uuid(),
    customer_id         UUID        NOT NULL UNIQUE,    -- logical ref to onboarding.customers
    risk_score          SMALLINT    NOT NULL DEFAULT 0
                            CHECK (risk_score BETWEEN 0 AND 100),
    risk_level          VARCHAR(20) NOT NULL DEFAULT 'UNKNOWN'
                            CHECK (risk_level IN (
                                'LOW', 'MEDIUM', 'HIGH', 'UNACCEPTABLE', 'UNKNOWN'
                            )),
    verification_status VARCHAR(30) NOT NULL DEFAULT 'PENDING'
                            CHECK (verification_status IN (
                                'PENDING',
                                'IN_PROGRESS',
                                'APPROVED',
                                'REJECTED',
                                'MANUAL_REVIEW',
                                'RE_VERIFICATION_REQUIRED'
                            )),
    pep_hit             BOOLEAN     NOT NULL DEFAULT FALSE,
    sanction_hit        BOOLEAN     NOT NULL DEFAULT FALSE,
    adverse_media       BOOLEAN     NOT NULL DEFAULT FALSE,
    approved_by         UUID,                           -- compliance officer user_id
    approved_at         TIMESTAMPTZ,
    rejection_reason    TEXT,
    review_notes        TEXT,
    next_review_date    DATE,                           -- periodic re-verification trigger
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    version             BIGINT      NOT NULL DEFAULT 0
);

CREATE INDEX idx_kyc_customer ON kyc_aml.kyc_profiles (customer_id);
CREATE INDEX idx_kyc_status   ON kyc_aml.kyc_profiles (verification_status);
CREATE INDEX idx_kyc_review   ON kyc_aml.kyc_profiles (next_review_date)
    WHERE next_review_date IS NOT NULL AND verification_status = 'APPROVED';

-- ------------------------------------------------------------
-- 4.2 PROVIDER VERIFICATION RESULTS
-- ------------------------------------------------------------
CREATE TABLE kyc_aml.provider_results (
    result_id        UUID        PRIMARY KEY  DEFAULT gen_random_uuid(),
    kyc_id           UUID        NOT NULL REFERENCES kyc_aml.kyc_profiles(kyc_id),
    provider         VARCHAR(30) NOT NULL
                         CHECK (provider IN ('JUMIO', 'ONFIDO', 'TRULIOO', 'MANUAL', 'INTERNAL')),
    provider_ref     VARCHAR(100),                      -- provider's own reference/case ID
    check_type       VARCHAR(50) NOT NULL
                         CHECK (check_type IN (
                             'IDENTITY_VERIFICATION',
                             'DOCUMENT_VERIFICATION',
                             'LIVENESS_CHECK',
                             'SANCTION_SCREENING',
                             'PEP_SCREENING',
                             'ADVERSE_MEDIA',
                             'ADDRESS_VERIFICATION'
                         )),
    result           VARCHAR(20) NOT NULL
                         CHECK (result IN ('PASS', 'FAIL', 'REVIEW', 'ERROR', 'PENDING')),
    confidence_score NUMERIC(5,4)
                         CHECK (confidence_score BETWEEN 0 AND 1),
    raw_response     JSONB,
    error_details    TEXT,
    received_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_provider_kyc    ON kyc_aml.provider_results (kyc_id);
CREATE INDEX idx_provider_result ON kyc_aml.provider_results (result);

-- ------------------------------------------------------------
-- 4.3 KYC DOCUMENTS (metadata — actual files in S3)
-- ------------------------------------------------------------
CREATE TABLE kyc_aml.kyc_documents (
    doc_id        UUID        PRIMARY KEY  DEFAULT gen_random_uuid(),
    kyc_id        UUID        NOT NULL REFERENCES kyc_aml.kyc_profiles(kyc_id),
    document_type VARCHAR(50) NOT NULL,
    s3_bucket     VARCHAR(100) NOT NULL,
    s3_key        VARCHAR(500) NOT NULL,
    content_type  VARCHAR(50)  NOT NULL,
    size_bytes    BIGINT       NOT NULL,
    status        VARCHAR(20)  NOT NULL DEFAULT 'SUBMITTED'
                      CHECK (status IN ('SUBMITTED', 'VERIFIED', 'REJECTED', 'EXPIRED')),
    submitted_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    verified_at   TIMESTAMPTZ,
    expires_at    DATE
);

CREATE INDEX idx_kycdoc_profile ON kyc_aml.kyc_documents (kyc_id);

-- ------------------------------------------------------------
-- 4.4 AML ALERTS (Aggregate Root)
-- ------------------------------------------------------------
CREATE TABLE kyc_aml.aml_alerts (
    alert_id        UUID        PRIMARY KEY  DEFAULT gen_random_uuid(),
    transaction_id  UUID        NOT NULL,               -- logical ref to banking.transactions
    customer_id     UUID        NOT NULL,               -- logical ref to onboarding.customers
    account_id      UUID        NOT NULL,               -- logical ref to banking.accounts
    alert_type      VARCHAR(50) NOT NULL
                        CHECK (alert_type IN (
                            'VELOCITY',
                            'STRUCTURING',
                            'PEP',
                            'SANCTION',
                            'GEO_ANOMALY',
                            'LARGE_CASH',
                            'ROUND_AMOUNT',
                            'UNUSUAL_PATTERN'
                        )),
    risk_score      SMALLINT    NOT NULL CHECK (risk_score BETWEEN 0 AND 100),
    status          VARCHAR(30) NOT NULL DEFAULT 'OPEN'
                        CHECK (status IN (
                            'OPEN',
                            'INVESTIGATING',
                            'SAR_FILED',
                            'CLEARED',
                            'FALSE_POSITIVE'
                        )),
    assigned_to     UUID,                               -- compliance officer user_id
    sar_id          UUID,                               -- populated when SAR is filed
    detection_rule  VARCHAR(100) NOT NULL,              -- rule name e.g. VELOCITY_10K_1H
    notes           TEXT,
    detected_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    assigned_at     TIMESTAMPTZ,
    resolved_at     TIMESTAMPTZ,
    sar_deadline    TIMESTAMPTZ  NOT NULL                -- set by trigger on INSERT
);

CREATE INDEX idx_alerts_customer ON kyc_aml.aml_alerts (customer_id, detected_at DESC);
CREATE INDEX idx_alerts_status   ON kyc_aml.aml_alerts (status)
    WHERE status IN ('OPEN', 'INVESTIGATING');
CREATE INDEX idx_alerts_deadline ON kyc_aml.aml_alerts (sar_deadline)
    WHERE status = 'OPEN';
CREATE INDEX idx_alerts_assigned ON kyc_aml.aml_alerts (assigned_to)
    WHERE assigned_to IS NOT NULL AND status IN ('OPEN', 'INVESTIGATING');

-- Trigger: auto-compute sar_deadline = detected_at + 30 days on INSERT
CREATE OR REPLACE FUNCTION kyc_aml.set_sar_deadline()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    NEW.sar_deadline := NEW.detected_at + INTERVAL '30 days';
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_aml_alerts_sar_deadline
    BEFORE INSERT ON kyc_aml.aml_alerts
    FOR EACH ROW EXECUTE FUNCTION kyc_aml.set_sar_deadline();

-- ------------------------------------------------------------
-- 4.5 RULE VIOLATIONS (evidence per alert)
-- ------------------------------------------------------------
CREATE TABLE kyc_aml.rule_violations (
    violation_id  UUID         PRIMARY KEY  DEFAULT gen_random_uuid(),
    alert_id      UUID         NOT NULL REFERENCES kyc_aml.aml_alerts(alert_id),
    rule_name     VARCHAR(100) NOT NULL,
    rule_version  VARCHAR(20)  NOT NULL,
    threshold     JSONB        NOT NULL,   -- e.g. {"amount": 10000, "window": "1h", "count": 5}
    actual_value  JSONB        NOT NULL,   -- what was observed
    triggered_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_violations_alert ON kyc_aml.rule_violations (alert_id);

-- ------------------------------------------------------------
-- 4.6 SAR REPORTS (Suspicious Activity Reports)
-- ------------------------------------------------------------
CREATE TABLE kyc_aml.sar_reports (
    sar_id           UUID         PRIMARY KEY  DEFAULT gen_random_uuid(),
    alert_id         UUID         NOT NULL REFERENCES kyc_aml.aml_alerts(alert_id),
    filing_number    VARCHAR(50)  UNIQUE,               -- regulatory body's assigned number
    filing_type      VARCHAR(30)  NOT NULL DEFAULT 'INITIAL'
                         CHECK (filing_type IN ('INITIAL', 'SUPPLEMENTAL', 'CONTINUATION')),
    filed_by         UUID         NOT NULL,             -- compliance officer user_id
    filed_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    reporting_period_start DATE,
    reporting_period_end   DATE,
    narrative        TEXT         NOT NULL,             -- human-written description
    s3_document_key  TEXT,                              -- SAR PDF in S3
    acknowledgement  TEXT,                              -- regulatory body acknowledgement
    status           VARCHAR(20)  NOT NULL DEFAULT 'DRAFT'
                         CHECK (status IN ('DRAFT', 'SUBMITTED', 'ACKNOWLEDGED', 'REJECTED'))
);

CREATE INDEX idx_sar_alert  ON kyc_aml.sar_reports (alert_id);
CREATE INDEX idx_sar_status ON kyc_aml.sar_reports (status);

-- ------------------------------------------------------------
-- 4.7 SANCTION/PEP SCREENING RESULTS (per transaction)
-- ------------------------------------------------------------
CREATE TABLE kyc_aml.screening_results (
    screening_id    UUID        PRIMARY KEY  DEFAULT gen_random_uuid(),
    entity_type     VARCHAR(20) NOT NULL CHECK (entity_type IN ('CUSTOMER', 'COUNTERPARTY')),
    entity_id       UUID        NOT NULL,
    transaction_id  UUID,                               -- if transaction-level screening
    list_type       VARCHAR(30) NOT NULL
                        CHECK (list_type IN ('OFAC', 'UN_SANCTIONS', 'EU_SANCTIONS', 'PEP', 'INTERPOL', 'FATF')),
    match_status    VARCHAR(20) NOT NULL
                        CHECK (match_status IN ('NO_MATCH', 'POTENTIAL_MATCH', 'CONFIRMED_MATCH', 'FALSE_POSITIVE')),
    match_score     NUMERIC(5,4),
    matched_name    TEXT,
    screened_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    cleared_at      TIMESTAMPTZ,
    cleared_by      UUID
);

CREATE INDEX idx_screening_entity ON kyc_aml.screening_results (entity_id, screened_at DESC);
CREATE INDEX idx_screening_status ON kyc_aml.screening_results (match_status)
    WHERE match_status IN ('POTENTIAL_MATCH', 'CONFIRMED_MATCH');

-- ------------------------------------------------------------
-- 4.8 OUTBOX EVENTS
-- ------------------------------------------------------------
CREATE TABLE kyc_aml.outbox_events (
    event_id       UUID         PRIMARY KEY  DEFAULT gen_random_uuid(),
    aggregate_type VARCHAR(50)  NOT NULL,
    aggregate_id   UUID         NOT NULL,
    event_type     VARCHAR(100) NOT NULL,
    payload        JSONB        NOT NULL,
    status         VARCHAR(20)  NOT NULL DEFAULT 'PENDING'
                       CHECK (status IN ('PENDING', 'PUBLISHED', 'FAILED')),
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    published_at   TIMESTAMPTZ,
    retry_count    SMALLINT     NOT NULL DEFAULT 0
);

CREATE INDEX idx_kyc_outbox_pending ON kyc_aml.outbox_events (created_at ASC)
    WHERE status = 'PENDING';


-- =============================================================
-- 5. NOTIFICATION SCHEMA
-- =============================================================

-- ------------------------------------------------------------
-- 5.1 NOTIFICATION TEMPLATES
-- ------------------------------------------------------------
CREATE TABLE notification.templates (
    template_id   UUID         PRIMARY KEY  DEFAULT gen_random_uuid(),
    name          VARCHAR(100) NOT NULL UNIQUE,
    channel       VARCHAR(20)  NOT NULL CHECK (channel IN ('EMAIL', 'SMS', 'PUSH')),
    event_type    VARCHAR(100) NOT NULL,               -- maps to domain event type
    subject       VARCHAR(500),                        -- email subject (NULL for SMS/push)
    body_template TEXT         NOT NULL,               -- Mustache/Handlebars template
    locale        VARCHAR(10)  NOT NULL DEFAULT 'en',
    is_active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_templates_event ON notification.templates (event_type, channel, locale)
    WHERE is_active = TRUE;

-- ------------------------------------------------------------
-- 5.2 NOTIFICATION LOG
-- ------------------------------------------------------------
CREATE TABLE notification.notification_log (
    notification_id  UUID        PRIMARY KEY  DEFAULT gen_random_uuid(),
    customer_id      UUID        NOT NULL,
    channel          VARCHAR(20) NOT NULL CHECK (channel IN ('EMAIL', 'SMS', 'PUSH')),
    recipient        VARCHAR(254) NOT NULL,             -- email address, phone, or device token
    template_id      UUID        REFERENCES notification.templates(template_id),
    event_type       VARCHAR(100) NOT NULL,
    subject          VARCHAR(500),
    body             TEXT        NOT NULL,
    status           VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                         CHECK (status IN ('PENDING', 'SENT', 'DELIVERED', 'FAILED', 'BOUNCED')),
    provider         VARCHAR(30),                       -- SES, SNS, FCM
    provider_ref     VARCHAR(200),                      -- provider's message ID
    error_message    TEXT,
    correlation_id   UUID,                              -- trace the originating domain event
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    sent_at          TIMESTAMPTZ,
    delivered_at     TIMESTAMPTZ
);

CREATE INDEX idx_notif_customer ON notification.notification_log (customer_id, created_at DESC);
CREATE INDEX idx_notif_status   ON notification.notification_log (status)
    WHERE status IN ('PENDING', 'FAILED');
CREATE INDEX idx_notif_corr     ON notification.notification_log (correlation_id);

-- ------------------------------------------------------------
-- 5.3 NOTIFICATION PREFERENCES (per customer)
-- ------------------------------------------------------------
CREATE TABLE notification.preferences (
    preference_id UUID        PRIMARY KEY  DEFAULT gen_random_uuid(),
    customer_id   UUID        NOT NULL UNIQUE,
    email_enabled BOOLEAN     NOT NULL DEFAULT TRUE,
    sms_enabled   BOOLEAN     NOT NULL DEFAULT TRUE,
    push_enabled  BOOLEAN     NOT NULL DEFAULT TRUE,
    -- per-event-type overrides stored as JSONB
    -- e.g. {"TransactionPosted": {"email": false, "push": true}}
    overrides     JSONB       NOT NULL DEFAULT '{}',
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_prefs_customer ON notification.preferences (customer_id);


-- =============================================================
-- 6. AUDIT SCHEMA — Immutable Event Log
-- =============================================================

-- ------------------------------------------------------------
-- 6.1 AUDIT LOG (append-only — never UPDATE or DELETE)
-- ------------------------------------------------------------
CREATE TABLE audit.audit_log (
    audit_id       UUID         PRIMARY KEY  DEFAULT gen_random_uuid(),
    event_id       UUID         NOT NULL UNIQUE,        -- original domain event_id
    event_type     VARCHAR(100) NOT NULL,
    aggregate_type VARCHAR(50)  NOT NULL,
    aggregate_id   UUID         NOT NULL,
    source_service VARCHAR(50)  NOT NULL,               -- which service published this
    actor_id       UUID,                                -- user who caused the event (if applicable)
    actor_type     VARCHAR(20)
                       CHECK (actor_type IN ('CUSTOMER', 'TELLER', 'SYSTEM', 'COMPLIANCE_OFFICER', 'ADMIN')),
    payload        JSONB        NOT NULL,               -- full event payload
    ip_address     INET,
    user_agent     TEXT,
    correlation_id UUID,                                -- for tracing request chains
    causation_id   UUID,                                -- event that caused this event
    occurred_at    TIMESTAMPTZ  NOT NULL,               -- when the business event happened
    recorded_at    TIMESTAMPTZ  NOT NULL DEFAULT now()  -- when audit log received it
);

-- Partial indexes for common compliance queries
CREATE INDEX idx_audit_aggregate   ON audit.audit_log (aggregate_type, aggregate_id, occurred_at DESC);
CREATE INDEX idx_audit_event_type  ON audit.audit_log (event_type, occurred_at DESC);
CREATE INDEX idx_audit_actor       ON audit.audit_log (actor_id, occurred_at DESC)
    WHERE actor_id IS NOT NULL;
CREATE INDEX idx_audit_occurred    ON audit.audit_log (occurred_at DESC);
CREATE INDEX idx_audit_correlation ON audit.audit_log (correlation_id)
    WHERE correlation_id IS NOT NULL;

-- ------------------------------------------------------------
-- 6.2 REGULATORY REPORTS
-- ------------------------------------------------------------
CREATE TABLE audit.regulatory_reports (
    report_id    UUID        PRIMARY KEY  DEFAULT gen_random_uuid(),
    report_type  VARCHAR(50) NOT NULL
                     CHECK (report_type IN (
                         'CTR',          -- Currency Transaction Report (>$10k cash)
                         'SAR',          -- Suspicious Activity Report
                         'OFAC_MATCH',   -- OFAC Sanctions Match
                         'GDPR_EXPORT',  -- GDPR Data Export
                         'PCI_AUDIT'     -- PCI-DSS Audit Trail
                     )),
    period_start DATE        NOT NULL,
    period_end   DATE        NOT NULL,
    generated_by UUID        NOT NULL,
    generated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    s3_key       TEXT,                                  -- report file in S3
    status       VARCHAR(20) NOT NULL DEFAULT 'GENERATING'
                     CHECK (status IN ('GENERATING', 'READY', 'SUBMITTED', 'FAILED')),
    submitted_at TIMESTAMPTZ,
    metadata     JSONB
);

CREATE INDEX idx_reports_type   ON audit.regulatory_reports (report_type, period_start DESC);
CREATE INDEX idx_reports_status ON audit.regulatory_reports (status);


-- =============================================================
-- 7. CROSS-SCHEMA HELPER VIEWS (read-only, for reporting)
-- NOTE: These are the ONLY permitted cross-schema references.
--       Application code must NEVER join across schemas directly.
-- =============================================================

-- Customer 360 view — available to reporting/ops tools only
CREATE VIEW audit.customer_360 AS
SELECT
    c.customer_id,
    c.first_name || ' ' || c.last_name AS full_name,
    c.tier,
    c.national_id_type,
    c.created_at                        AS onboarded_at,
    k.risk_level,
    k.pep_hit,
    k.sanction_hit,
    k.verification_status               AS kyc_status,
    (SELECT COUNT(*) FROM kyc_aml.aml_alerts a
        WHERE a.customer_id = c.customer_id
          AND a.status IN ('OPEN', 'INVESTIGATING')
    )                                   AS open_aml_alerts
FROM onboarding.customers       c
LEFT JOIN kyc_aml.kyc_profiles  k ON k.customer_id = c.customer_id;


-- =============================================================
-- 8. VERIFY — Count tables per schema
-- =============================================================
SELECT
    schemaname,
    COUNT(*) AS table_count
FROM pg_tables
WHERE schemaname IN ('iam', 'onboarding', 'banking', 'kyc_aml', 'notification', 'audit')
GROUP BY schemaname
ORDER BY schemaname;
