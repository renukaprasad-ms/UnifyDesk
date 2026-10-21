-- Inbound SMTP staging: one delivery per mailbox and upstream delivery key.
-- No public ingress, scanner, or mailbox reader is activated by this migration.
-- Raw MIME is stored on a persistent, private filesystem volume, not in PostgreSQL.
CREATE TABLE email_mail_inbound_jobs (
  id BIGINT PRIMARY KEY,
  organization_id BIGINT NOT NULL,
  mailbox_id BIGINT NOT NULL,
  delivery_key VARCHAR(128) NOT NULL,
  status VARCHAR(24) NOT NULL DEFAULT 'PENDING_SCAN',
  mime_object_key VARCHAR(64) NOT NULL UNIQUE,
  mime_size_bytes BIGINT NOT NULL,
  sha256 VARCHAR(64) NOT NULL,
  received_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT fk_mail_inbound_mailbox_org
    FOREIGN KEY (organization_id,mailbox_id)
    REFERENCES email_mailboxes(organization_id,id),
  CONSTRAINT uq_mail_inbound_delivery UNIQUE (mailbox_id,delivery_key),
  CONSTRAINT chk_mail_inbound_status CHECK
    (status IN ('PENDING_SCAN','SCANNING','QUARANTINED','READY','REJECTED')),
  CONSTRAINT chk_mail_inbound_size CHECK (mime_size_bytes BETWEEN 1 AND 10485760)
);
CREATE INDEX idx_mail_inbound_jobs_pending
  ON email_mail_inbound_jobs(received_at,id) WHERE status='PENDING_SCAN';
CREATE INDEX idx_mail_inbound_jobs_org ON email_mail_inbound_jobs(organization_id,mailbox_id);
