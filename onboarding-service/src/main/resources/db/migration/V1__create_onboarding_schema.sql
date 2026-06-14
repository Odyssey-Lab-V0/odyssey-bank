-- Onboarding Service schema — owned exclusively by this service
CREATE SCHEMA IF NOT EXISTS onboarding;

CREATE TABLE onboarding.customers (
    id          UUID PRIMARY KEY,
    user_id     UUID        NOT NULL UNIQUE,   -- ref to iam.users (no FK — cross-schema)
    email       VARCHAR(255) NOT NULL,
    first_name  VARCHAR(100) NOT NULL,
    last_name   VARCHAR(100) NOT NULL,
    status      VARCHAR(30)  NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_customers_user_id ON onboarding.customers (user_id);
CREATE INDEX idx_customers_email   ON onboarding.customers (email);

CREATE TABLE onboarding.outbox_events (
    event_id       UUID        PRIMARY KEY,
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id   UUID        NOT NULL,
    event_type     VARCHAR(100) NOT NULL,
    payload        TEXT        NOT NULL,
    status         VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    published_at   TIMESTAMPTZ
);

CREATE INDEX idx_onboarding_outbox_pending ON onboarding.outbox_events (status, created_at)
    WHERE status = 'PENDING';
