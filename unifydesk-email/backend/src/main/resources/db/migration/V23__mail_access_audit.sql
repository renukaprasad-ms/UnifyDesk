-- Content-free, append-only application audit for explicit mailbox grants and reads.
ALTER TABLE email_mailbox_access
  ADD COLUMN can_read BOOLEAN NOT NULL DEFAULT TRUE,
  ADD COLUMN can_send BOOLEAN NOT NULL DEFAULT TRUE,
  ADD COLUMN can_delegate BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE email_mail_audit (
  id BIGINT PRIMARY KEY,
  organization_id BIGINT NOT NULL,
  mailbox_id BIGINT NOT NULL,
  actor_user_id BIGINT NOT NULL,
  target_user_id BIGINT,
  message_id BIGINT,
  action VARCHAR(32) NOT NULL,
  occurred_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  FOREIGN KEY (organization_id,mailbox_id) REFERENCES email_mailboxes(organization_id,id),
  FOREIGN KEY (organization_id,actor_user_id) REFERENCES email_console_users(organization_id,id),
  FOREIGN KEY (organization_id,target_user_id) REFERENCES email_console_users(organization_id,id),
  CHECK (action IN ('ACTIVATE','GRANT','REVOKE','READ_PREVIEW','DOWNLOAD_RAW','LIST_ATTACHMENTS',
    'DOWNLOAD_ATTACHMENT','MOVE_MESSAGE','RESTORE_MESSAGE'))
);
CREATE INDEX idx_mail_audit_org ON email_mail_audit(organization_id,id DESC);
CREATE INDEX idx_mail_audit_mailbox ON email_mail_audit(organization_id,mailbox_id,id DESC);
