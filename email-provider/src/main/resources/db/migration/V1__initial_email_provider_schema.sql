CREATE TABLE sending_domains (
    id BIGINT PRIMARY KEY,
    company_id BIGINT NOT NULL,
    domain_name VARCHAR(253) NOT NULL,
    status VARCHAR(32) NOT NULL,
    verification_token VARCHAR(128) NOT NULL,
    verified_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_sending_domains_domain_name UNIQUE (domain_name)
);

CREATE INDEX idx_sending_domains_company_id ON sending_domains (company_id);
CREATE INDEX idx_sending_domains_status ON sending_domains (status);

CREATE TABLE email_messages (
    id BIGINT PRIMARY KEY,
    company_id BIGINT NOT NULL,
    sending_domain_id BIGINT NOT NULL,
    from_address VARCHAR(320) NOT NULL,
    reply_to_address VARCHAR(320) NULL,
    subject VARCHAR(998) NOT NULL,
    text_body TEXT NULL,
    html_body TEXT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_email_messages_sending_domain
        FOREIGN KEY (sending_domain_id) REFERENCES sending_domains (id)
);

CREATE INDEX idx_email_messages_company_id ON email_messages (company_id);
CREATE INDEX idx_email_messages_sending_domain_id ON email_messages (sending_domain_id);
CREATE INDEX idx_email_messages_status ON email_messages (status);
CREATE INDEX idx_email_messages_created_at ON email_messages (created_at);

CREATE TABLE email_recipients (
    id BIGINT PRIMARY KEY,
    message_id BIGINT NOT NULL,
    recipient_address VARCHAR(320) NOT NULL,
    recipient_type VARCHAR(16) NOT NULL,
    status VARCHAR(32) NOT NULL,
    smtp_status_code VARCHAR(16) NULL,
    smtp_response VARCHAR(2048) NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_email_recipients_message
        FOREIGN KEY (message_id) REFERENCES email_messages (id) ON DELETE CASCADE
);

CREATE INDEX idx_email_recipients_message_id ON email_recipients (message_id);
CREATE INDEX idx_email_recipients_address ON email_recipients (recipient_address);
CREATE INDEX idx_email_recipients_status ON email_recipients (status);
