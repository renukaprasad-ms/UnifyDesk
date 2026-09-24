CREATE TABLE email_delivery_attempts (
    id BIGINT PRIMARY KEY,
    recipient_id BIGINT NOT NULL,
    attempt_number INTEGER NOT NULL,
    outcome VARCHAR(32) NOT NULL,
    smtp_status_code VARCHAR(16) NULL,
    smtp_response VARCHAR(2048) NULL,
    started_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_email_delivery_attempt_recipient
        FOREIGN KEY (recipient_id) REFERENCES email_recipients (id) ON DELETE CASCADE
);

CREATE INDEX idx_email_delivery_attempt_recipient
    ON email_delivery_attempts (recipient_id, attempt_number);
