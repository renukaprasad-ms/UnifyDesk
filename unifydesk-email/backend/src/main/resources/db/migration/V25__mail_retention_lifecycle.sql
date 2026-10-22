ALTER TABLE email_mailboxes
  ADD COLUMN trash_retention_days INT NOT NULL DEFAULT 0 CHECK(trash_retention_days=0 OR trash_retention_days BETWEEN 7 AND 3650),
  ADD COLUMN legal_hold BOOLEAN NOT NULL DEFAULT FALSE,
  ADD COLUMN backup_verified_at TIMESTAMPTZ,
  ADD COLUMN backup_reference VARCHAR(160);
ALTER TABLE email_mailbox_messages
  ADD COLUMN previous_folder VARCHAR(24),
  ADD COLUMN trashed_at TIMESTAMPTZ,
  ADD COLUMN purged_at TIMESTAMPTZ;
-- Earlier archives occupied disk without a mailbox quota reservation.
ALTER TABLE email_mail_inbound_jobs ADD CONSTRAINT uq_mail_inbound_tenant_ref
  UNIQUE(organization_id,mailbox_id,id);
ALTER TABLE email_mailbox_messages ADD CONSTRAINT fk_mail_message_inbound_tenant
  FOREIGN KEY(organization_id,mailbox_id,inbound_job_id)
  REFERENCES email_mail_inbound_jobs(organization_id,mailbox_id,id);

UPDATE email_mailboxes m SET storage_used_bytes=m.storage_used_bytes+a.bytes
FROM (SELECT organization_id,mailbox_id,sum(mime_size_bytes) AS bytes
      FROM email_mail_outbound_archives GROUP BY organization_id,mailbox_id) a
WHERE m.id=a.mailbox_id AND m.organization_id=a.organization_id;
CREATE INDEX idx_mail_retention_due ON email_mailbox_messages(trashed_at,id)
  WHERE folder='TRASH' AND deleted_at IS NOT NULL AND purged_at IS NULL;
-- Only the sole previously granted user can become the initial delegate.
-- Ambiguous legacy multi-user ownership requires private operator review.
UPDATE email_mailbox_access a SET can_delegate=TRUE
WHERE NOT EXISTS(SELECT 1 FROM email_mailbox_access other
  WHERE other.mailbox_id=a.mailbox_id AND other.user_id<>a.user_id);

-- Durable PURGING is committed before any filesystem removal. A crash after
-- unlink cannot make an object restorable or release its storage quota twice.
CREATE TABLE email_mail_object_gc (
  object_key VARCHAR(64) PRIMARY KEY,
  organization_id BIGINT NOT NULL,
  mailbox_id BIGINT NOT NULL,
  message_id BIGINT NOT NULL UNIQUE,
  size_bytes BIGINT NOT NULL CHECK(size_bytes>0),
  state VARCHAR(16) NOT NULL DEFAULT 'PURGING' CHECK(state IN ('PURGING','PURGED')),
  marked_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  completed_at TIMESTAMPTZ,
  FOREIGN KEY(organization_id,mailbox_id,message_id)
    REFERENCES email_mailbox_messages(organization_id,mailbox_id,id)
);
CREATE INDEX idx_mail_object_gc_pending ON email_mail_object_gc(marked_at) WHERE state='PURGING';
CREATE TABLE email_mail_retention_audit (
  id BIGINT PRIMARY KEY,
  organization_id BIGINT NOT NULL,
  mailbox_id BIGINT NOT NULL,
  message_id BIGINT NOT NULL,
  action VARCHAR(24) NOT NULL CHECK(action IN ('PURGE_MARKED','PURGE_COMPLETED')),
  occurred_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  FOREIGN KEY(organization_id,mailbox_id,message_id)
    REFERENCES email_mailbox_messages(organization_id,mailbox_id,id)
);
