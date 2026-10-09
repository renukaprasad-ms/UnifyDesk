ALTER TABLE email_recipients
    ADD COLUMN postfix_queue_id VARCHAR(128) NULL;

CREATE INDEX idx_email_recipients_postfix_queue_id
    ON email_recipients (postfix_queue_id);

CREATE TABLE email_events (
    id BIGINT PRIMARY KEY,
    company_id BIGINT NOT NULL,
    message_id BIGINT NOT NULL,
    recipient_id BIGINT NOT NULL,
    event_type VARCHAR(32) NOT NULL,
    source VARCHAR(32) NOT NULL,
    external_event_id VARCHAR(256) NULL,
    smtp_status_code VARCHAR(32) NULL,
    diagnostic VARCHAR(2048) NULL,
    raw_payload TEXT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_email_events_message
        FOREIGN KEY (message_id) REFERENCES email_messages (id) ON DELETE CASCADE,
    CONSTRAINT fk_email_events_recipient
        FOREIGN KEY (recipient_id) REFERENCES email_recipients (id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX uk_email_events_source_external
    ON email_events (source, external_event_id)
    WHERE external_event_id IS NOT NULL;

CREATE INDEX idx_email_events_message
    ON email_events (message_id, occurred_at);

CREATE INDEX idx_email_events_recipient
    ON email_events (recipient_id, occurred_at);

CREATE TABLE email_reputation_daily (
    id BIGINT PRIMARY KEY,
    company_id BIGINT NOT NULL,
    sending_domain_id BIGINT NOT NULL,
    metric_date DATE NOT NULL,
    accepted_count BIGINT NOT NULL DEFAULT 0,
    delivered_count BIGINT NOT NULL DEFAULT 0,
    bounced_count BIGINT NOT NULL DEFAULT 0,
    deferred_count BIGINT NOT NULL DEFAULT 0,
    complaint_count BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_email_reputation_daily
        UNIQUE (company_id, sending_domain_id, metric_date),
    CONSTRAINT fk_email_reputation_domain
        FOREIGN KEY (sending_domain_id) REFERENCES sending_domains (id) ON DELETE CASCADE
);

CREATE INDEX idx_email_reputation_company_date
    ON email_reputation_daily (company_id, metric_date);
