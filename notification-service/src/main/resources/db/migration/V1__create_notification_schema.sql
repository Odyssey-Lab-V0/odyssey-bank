CREATE SCHEMA IF NOT EXISTS notification;

CREATE TABLE notification.notification_log (
    id              UUID        PRIMARY KEY,
    recipient_id    UUID,                           -- userId or customerId (cross-service ref, no FK)
    recipient       VARCHAR(255) NOT NULL,           -- email or phone
    channel         VARCHAR(10)  NOT NULL,
    subject         VARCHAR(500) NOT NULL,
    body            TEXT         NOT NULL,
    trigger_event   VARCHAR(100) NOT NULL,
    status          VARCHAR(10)  NOT NULL,
    sent_at         TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_notification_log_recipient_id ON notification.notification_log (recipient_id);
CREATE INDEX idx_notification_log_sent_at      ON notification.notification_log (sent_at DESC);
