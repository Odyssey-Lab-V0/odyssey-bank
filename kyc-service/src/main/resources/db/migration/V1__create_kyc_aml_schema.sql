CREATE SCHEMA IF NOT EXISTS kyc_aml;

CREATE TABLE kyc_aml.kyc_cases (
    id              UUID         PRIMARY KEY,
    customer_id     UUID         NOT NULL UNIQUE,
    user_id         UUID         NOT NULL,
    email           VARCHAR(255) NOT NULL,
    status          VARCHAR(20)  NOT NULL,
    risk_level      VARCHAR(10),
    risk_score      INTEGER,
    reviewer_notes  TEXT,
    created_at      TIMESTAMPTZ  NOT NULL,
    reviewed_at     TIMESTAMPTZ
);

CREATE INDEX idx_kyc_cases_customer_id ON kyc_aml.kyc_cases (customer_id);
CREATE INDEX idx_kyc_cases_status      ON kyc_aml.kyc_cases (status);

CREATE TABLE kyc_aml.kyc_outbox_events (
    event_id       UUID         PRIMARY KEY,
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id   UUID         NOT NULL,
    event_type     VARCHAR(100) NOT NULL,
    payload        TEXT         NOT NULL,
    status         VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    published_at   TIMESTAMPTZ
);

CREATE INDEX idx_kyc_outbox_pending ON kyc_aml.kyc_outbox_events (status, created_at)
    WHERE status = 'PENDING';

CREATE TABLE kyc_aml.aml_alerts (
    id              UUID        PRIMARY KEY,
    account_id      UUID        NOT NULL,
    transaction_id  UUID,
    alert_type      VARCHAR(30) NOT NULL,
    status          VARCHAR(20) NOT NULL,
    amount          NUMERIC(19,4),
    description     TEXT        NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL,
    reviewed_at     TIMESTAMPTZ
);

CREATE INDEX idx_aml_alerts_account_id  ON kyc_aml.aml_alerts (account_id);
CREATE INDEX idx_aml_alerts_status      ON kyc_aml.aml_alerts (status);
