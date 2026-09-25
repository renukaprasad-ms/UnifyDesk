ALTER TABLE sending_domains
    ADD COLUMN ownership_verified_at TIMESTAMPTZ NULL,
    ADD COLUMN dkim_verified_at TIMESTAMPTZ NULL,
    ADD COLUMN last_checked_at TIMESTAMPTZ NULL;

CREATE TABLE dkim_keys (
    id BIGINT PRIMARY KEY,
    sending_domain_id BIGINT NOT NULL,
    selector VARCHAR(63) NOT NULL,
    encrypted_private_key TEXT NOT NULL,
    public_key TEXT NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    verified_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_dkim_keys_domain_selector UNIQUE (sending_domain_id, selector),
    CONSTRAINT fk_dkim_keys_sending_domain
        FOREIGN KEY (sending_domain_id) REFERENCES sending_domains (id) ON DELETE CASCADE
);

CREATE INDEX idx_dkim_keys_sending_domain_id ON dkim_keys (sending_domain_id);
CREATE INDEX idx_dkim_keys_active ON dkim_keys (active);
