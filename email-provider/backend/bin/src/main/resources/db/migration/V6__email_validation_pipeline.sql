CREATE TABLE email_validation_jobs (
    id BIGINT PRIMARY KEY,
    company_id BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    total_count BIGINT NOT NULL DEFAULT 0,
    duplicate_count BIGINT NOT NULL DEFAULT 0,
    processed_count BIGINT NOT NULL DEFAULT 0,
    valid_count BIGINT NOT NULL DEFAULT 0,
    invalid_count BIGINT NOT NULL DEFAULT 0,
    risky_count BIGINT NOT NULL DEFAULT 0,
    unknown_count BIGINT NOT NULL DEFAULT 0,
    total_batches INTEGER NOT NULL DEFAULT 0,
    completed_batches INTEGER NOT NULL DEFAULT 0,
    failed_batches INTEGER NOT NULL DEFAULT 0,
    started_at TIMESTAMPTZ NULL,
    completed_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_validation_jobs_company_created
    ON email_validation_jobs (company_id, created_at DESC);
CREATE INDEX idx_validation_jobs_status
    ON email_validation_jobs (status);

CREATE TABLE email_validation_batches (
    id BIGINT PRIMARY KEY,
    job_id BIGINT NOT NULL,
    batch_sequence INTEGER NOT NULL,
    item_count INTEGER NOT NULL,
    status VARCHAR(32) NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NULL,
    started_at TIMESTAMPTZ NULL,
    completed_at TIMESTAMPTZ NULL,
    last_error VARCHAR(2048) NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_validation_batches_job
        FOREIGN KEY (job_id) REFERENCES email_validation_jobs (id) ON DELETE CASCADE,
    CONSTRAINT uk_validation_batches_job_sequence UNIQUE (job_id, batch_sequence)
);

CREATE INDEX idx_validation_batches_job
    ON email_validation_batches (job_id, batch_sequence);
CREATE INDEX idx_validation_batches_retry
    ON email_validation_batches (status, next_attempt_at);

CREATE TABLE email_validation_items (
    id BIGINT PRIMARY KEY,
    job_id BIGINT NOT NULL,
    batch_id BIGINT NOT NULL,
    original_email VARCHAR(1024) NOT NULL,
    dedupe_key VARCHAR(64) NOT NULL,
    normalized_email VARCHAR(320) NULL,
    domain VARCHAR(253) NULL,
    status VARCHAR(32) NULL,
    reason VARCHAR(64) NULL,
    score INTEGER NULL,
    syntax_valid BOOLEAN NULL,
    domain_routable BOOLEAN NULL,
    mx_found BOOLEAN NULL,
    disposable BOOLEAN NULL,
    role_address BOOLEAN NULL,
    catch_all BOOLEAN NULL,
    smtp_status VARCHAR(32) NULL,
    smtp_code INTEGER NULL,
    diagnostic VARCHAR(1024) NULL,
    validated_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_validation_items_job
        FOREIGN KEY (job_id) REFERENCES email_validation_jobs (id) ON DELETE CASCADE,
    CONSTRAINT fk_validation_items_batch
        FOREIGN KEY (batch_id) REFERENCES email_validation_batches (id) ON DELETE CASCADE,
    CONSTRAINT uk_validation_items_job_dedupe UNIQUE (job_id, dedupe_key)
);

CREATE INDEX idx_validation_items_job_id
    ON email_validation_items (job_id, id);
CREATE INDEX idx_validation_items_batch_id
    ON email_validation_items (batch_id, id);
CREATE INDEX idx_validation_items_domain
    ON email_validation_items (domain);
