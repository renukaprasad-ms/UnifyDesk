CREATE TABLE webhook_callbacks (
    id BIGINT PRIMARY KEY,
    provider_type VARCHAR(40) NOT NULL,
    callback_type VARCHAR(80) NOT NULL,
    endpoint VARCHAR(255) NOT NULL,
    http_method VARCHAR(12) NOT NULL,
    query_string TEXT,
    remote_address VARCHAR(128),
    content_type VARCHAR(160),
    event_id VARCHAR(255),
    request_headers TEXT,
    payload TEXT,
    decoded_payload TEXT,
    processing_result TEXT,
    status VARCHAR(24) NOT NULL,
    http_status INTEGER,
    error_message TEXT,
    received_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    duration_ms BIGINT
);

CREATE INDEX idx_webhook_callbacks_received_at
    ON webhook_callbacks (received_at DESC);

CREATE INDEX idx_webhook_callbacks_provider_status_received
    ON webhook_callbacks (provider_type, status, received_at DESC);

CREATE INDEX idx_webhook_callbacks_event_id
    ON webhook_callbacks (event_id);
