CREATE TABLE email_validation_cache (
    id BIGINT PRIMARY KEY,
    normalized_email VARCHAR(320) NOT NULL UNIQUE,
    status VARCHAR(32) NOT NULL,
    reason VARCHAR(64) NOT NULL,
    score INTEGER NOT NULL,
    domain_routable BOOLEAN NOT NULL,
    mx_found BOOLEAN NOT NULL,
    disposable BOOLEAN NOT NULL,
    role_address BOOLEAN NOT NULL,
    catch_all BOOLEAN NULL,
    smtp_status VARCHAR(32) NULL,
    smtp_code INTEGER NULL,
    diagnostic VARCHAR(1024) NULL,
    validated_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_validation_cache_expires
    ON email_validation_cache (expires_at);

CREATE TABLE email_validation_domain_cache (
    id BIGINT PRIMARY KEY,
    domain VARCHAR(253) NOT NULL UNIQUE,
    mail_routable BOOLEAN NOT NULL,
    null_mx BOOLEAN NOT NULL,
    mx_found BOOLEAN NOT NULL,
    mx_hosts TEXT NULL,
    disposable BOOLEAN NOT NULL,
    catch_all BOOLEAN NULL,
    dns_lookup_failed BOOLEAN NOT NULL,
    checked_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_validation_domain_cache_expires
    ON email_validation_domain_cache (expires_at);
