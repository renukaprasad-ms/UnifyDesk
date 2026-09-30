CREATE INDEX IF NOT EXISTS idx_sender_identities_external_id
    ON sender_identities (external_id)
    WHERE external_id IS NOT NULL;

CREATE TABLE meta_whatsapp_connections (
    connection_id BIGINT PRIMARY KEY,
    company_id BIGINT NOT NULL,
    account_model_version VARCHAR(24) NOT NULL DEFAULT 'LEGACY_WABA',
    legacy_waba_id VARCHAR(255),
    whatsapp_account_id VARCHAR(255),
    messaging_account_id VARCHAR(255),
    selected_phone_number_id VARCHAR(255) NOT NULL,
    product_state VARCHAR(32) NOT NULL,
    auth_state VARCHAR(32) NOT NULL,
    registration_state VARCHAR(32) NOT NULL,
    subscription_state VARCHAR(32) NOT NULL,
    coexistence_state VARCHAR(32) NOT NULL DEFAULT 'DISABLED',
    coexistence_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    last_validated_at TIMESTAMPTZ,
    next_reconcile_at TIMESTAMPTZ,
    last_provider_http_status INTEGER,
    last_provider_error_type VARCHAR(120),
    last_provider_error_code INTEGER,
    last_provider_error_subcode INTEGER,
    last_provider_fbtrace_id VARCHAR(255),
    last_safe_error_code VARCHAR(80),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE UNIQUE INDEX uk_meta_whatsapp_connections_active_phone
    ON meta_whatsapp_connections (selected_phone_number_id)
    WHERE product_state <> 'DISCONNECTED';

CREATE INDEX idx_meta_whatsapp_connections_company_state
    ON meta_whatsapp_connections (company_id, product_state);

CREATE INDEX idx_meta_whatsapp_connections_phone
    ON meta_whatsapp_connections (selected_phone_number_id);

CREATE INDEX idx_meta_whatsapp_connections_next_reconcile
    ON meta_whatsapp_connections (next_reconcile_at);

CREATE TABLE meta_whatsapp_provisioning_operations (
    id BIGINT PRIMARY KEY,
    connection_id BIGINT NOT NULL,
    company_id BIGINT NOT NULL,
    operation_key VARCHAR(255) NOT NULL UNIQUE,
    operation_type VARCHAR(40) NOT NULL,
    state VARCHAR(24) NOT NULL,
    step VARCHAR(40) NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ,
    claim_token VARCHAR(80),
    claim_expires_at TIMESTAMPTZ,
    last_safe_error_code VARCHAR(80),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ
);

CREATE INDEX idx_meta_whatsapp_provisioning_connection
    ON meta_whatsapp_provisioning_operations (connection_id);

CREATE INDEX idx_meta_whatsapp_provisioning_company
    ON meta_whatsapp_provisioning_operations (company_id);

CREATE INDEX idx_meta_whatsapp_provisioning_due
    ON meta_whatsapp_provisioning_operations (next_attempt_at, id)
    WHERE state IN ('PENDING', 'FAILED_RETRYABLE');
