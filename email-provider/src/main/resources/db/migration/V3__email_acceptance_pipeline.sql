ALTER TABLE email_messages
    ADD COLUMN idempotency_key VARCHAR(128) NULL,
    ADD COLUMN request_hash VARCHAR(64) NULL,
    ADD COLUMN accepted_at TIMESTAMPTZ NULL;

CREATE UNIQUE INDEX uk_email_messages_company_idempotency
    ON email_messages (company_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;

ALTER TABLE email_recipients
    ADD COLUMN attempt_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN next_attempt_at TIMESTAMPTZ NULL,
    ADD COLUMN last_attempt_at TIMESTAMPTZ NULL,
    ADD COLUMN handed_off_at TIMESTAMPTZ NULL,
    ADD COLUMN delivered_at TIMESTAMPTZ NULL;

CREATE TABLE email_suppressions (
    id BIGINT PRIMARY KEY,
    company_id BIGINT NOT NULL,
    recipient_address VARCHAR(320) NOT NULL,
    reason VARCHAR(32) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    expires_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE UNIQUE INDEX uk_email_suppressions_active
    ON email_suppressions (company_id, recipient_address)
    WHERE active = TRUE;

CREATE INDEX idx_email_suppressions_lookup
    ON email_suppressions (company_id, recipient_address, active);

CREATE TABLE email_outbox (
    id BIGINT PRIMARY KEY,
    topic VARCHAR(200) NOT NULL,
    event_key VARCHAR(200) NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NULL,
    claimed_at TIMESTAMPTZ NULL,
    published_at TIMESTAMPTZ NULL,
    last_error VARCHAR(2048) NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_email_outbox_dispatch
    ON email_outbox (status, next_attempt_at, created_at);
