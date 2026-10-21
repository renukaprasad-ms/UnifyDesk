CREATE TABLE email_webhook_endpoints (
 id BIGINT PRIMARY KEY,
 project_id BIGINT NOT NULL REFERENCES email_product_projects(id),
 endpoint_url VARCHAR(1024) NOT NULL,
 encrypted_secret TEXT NOT NULL,
 enabled BOOLEAN NOT NULL DEFAULT TRUE,
 created_at TIMESTAMPTZ NOT NULL,
 updated_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_email_webhook_endpoints_project ON email_webhook_endpoints(project_id);

CREATE TABLE email_webhook_deliveries (
 id BIGINT PRIMARY KEY,
 endpoint_id BIGINT NOT NULL REFERENCES email_webhook_endpoints(id),
 event_id BIGINT NOT NULL REFERENCES email_events(id),
 payload TEXT NOT NULL,
 status VARCHAR(16) NOT NULL,
 attempts INTEGER NOT NULL DEFAULT 0,
 next_attempt_at TIMESTAMPTZ NOT NULL,
 claimed_at TIMESTAMPTZ,
 delivered_at TIMESTAMPTZ,
 last_error VARCHAR(256),
 created_at TIMESTAMPTZ NOT NULL,
 updated_at TIMESTAMPTZ NOT NULL,
 CONSTRAINT uq_email_webhook_event_endpoint UNIQUE(endpoint_id,event_id)
);
CREATE INDEX idx_email_webhook_due ON email_webhook_deliveries(status,next_attempt_at);
