-- R3: staged scanner, mailbox access ACL and exactly-once promotion.
-- All data remains non-public unless the scan worker AND activation are explicitly enabled.
ALTER TABLE email_console_users ADD CONSTRAINT uq_mail_console_user_org_id
  UNIQUE (organization_id, id);
CREATE TABLE email_mailbox_access (
  organization_id BIGINT NOT NULL,
  mailbox_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  granted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (mailbox_id, user_id),
  CONSTRAINT fk_mail_access_mailbox FOREIGN KEY (organization_id, mailbox_id)
    REFERENCES email_mailboxes (organization_id, id),
  CONSTRAINT fk_mail_access_user FOREIGN KEY (organization_id, user_id)
    REFERENCES email_console_users (organization_id, id)
);
CREATE INDEX idx_mail_access_user ON email_mailbox_access(organization_id, user_id, mailbox_id);

ALTER TABLE email_mail_inbound_jobs
  ADD COLUMN claim_token UUID,
  ADD COLUMN claimed_at TIMESTAMPTZ,
  ADD COLUMN scan_attempts INT NOT NULL DEFAULT 0,
  ADD COLUMN scan_retry_after TIMESTAMPTZ,
  ADD COLUMN scan_error VARCHAR(160),
  ADD CONSTRAINT chk_mail_scan_attempts CHECK (scan_attempts BETWEEN 0 AND 20);
CREATE INDEX idx_mail_scan_due ON email_mail_inbound_jobs(scan_retry_after, received_at, id)
  WHERE status IN ('PENDING_SCAN','SCANNING');

CREATE INDEX idx_mail_scan_stale ON email_mail_inbound_jobs(claimed_at)
  WHERE status='SCANNING';

ALTER TABLE email_mailbox_messages
  ADD COLUMN inbound_job_id BIGINT UNIQUE
    REFERENCES email_mail_inbound_jobs(id);
CREATE INDEX idx_mail_messages_cursor
  ON email_mailbox_messages(mailbox_id, folder, id DESC)
  WHERE deleted_at IS NULL;
