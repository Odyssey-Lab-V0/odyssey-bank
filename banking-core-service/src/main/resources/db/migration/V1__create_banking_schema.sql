-- Banking Core schema
CREATE SCHEMA IF NOT EXISTS banking;

CREATE TABLE IF NOT EXISTS banking.accounts (
    account_id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id             UUID NOT NULL,
    account_number          VARCHAR(16) NOT NULL UNIQUE,
    account_type            VARCHAR(20) NOT NULL CHECK (account_type IN ('CURRENT','SAVINGS','FIXED_DEPOSIT','LOAN')),
    status                  VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('PENDING_ACTIVATION','ACTIVE','SUSPENDED','CLOSED')),
    currency                CHAR(3) NOT NULL DEFAULT 'USD',
    balance                 NUMERIC(19,2) NOT NULL DEFAULT 0.00,
    daily_transaction_limit NUMERIC(19,2) NOT NULL DEFAULT 50000.00,
    daily_transacted_today  NUMERIC(19,2) NOT NULL DEFAULT 0.00,
    daily_limit_reset_at    TIMESTAMPTZ,
    opened_at               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    closed_at               TIMESTAMPTZ,
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version                 BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_accounts_customer ON banking.accounts(customer_id);
CREATE INDEX IF NOT EXISTS idx_accounts_status   ON banking.accounts(status);

-- Double-entry ledger — source of truth for balances
CREATE TABLE IF NOT EXISTS banking.ledger_entries (
    entry_id       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id UUID NOT NULL,
    account_id     UUID NOT NULL REFERENCES banking.accounts(account_id),
    entry_type     VARCHAR(6) NOT NULL CHECK (entry_type IN ('DEBIT','CREDIT')),
    amount         NUMERIC(19,2) NOT NULL CHECK (amount > 0),
    currency       CHAR(3) NOT NULL,
    description    TEXT,
    posted_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_ledger_account     ON banking.ledger_entries(account_id);
CREATE INDEX IF NOT EXISTS idx_ledger_transaction ON banking.ledger_entries(transaction_id);
CREATE INDEX IF NOT EXISTS idx_ledger_posted      ON banking.ledger_entries(posted_at DESC);

-- Outbox for domain events
CREATE TABLE IF NOT EXISTS banking.outbox_events (
    event_id       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type VARCHAR(50) NOT NULL,
    aggregate_id   UUID NOT NULL,
    event_type     VARCHAR(100) NOT NULL,
    payload        TEXT NOT NULL,
    status         VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','PUBLISHED','FAILED')),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    published_at   TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_banking_outbox_pending ON banking.outbox_events(created_at) WHERE status = 'PENDING';
