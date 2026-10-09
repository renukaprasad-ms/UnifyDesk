-- R8 mailbox archive and trusted delivery feedback, isolated from marketing.
-- Immutable raw MIME is in an existing encrypted persistent spool, not a DB BLOB.
CREATE TABLE email_mail_outbound_archives (
  intent_id BIGINT PRIMARY KEY,
  organization_id BIGINT NOT NULL,
  mailbox_id BIGINT NOT NULL,
  mime_object_key VARCHAR(64) NOT NULL UNIQUE,
  mime_size_bytes BIGINT NOT NULL,
  sha256 CHAR(64) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT fk_mail_archive_intent_org
    FOREIGN KEY (organization_id,mailbox_id,intent_id)
    REFERENCES email_mail_outbound_intents(organization_id,mailbox_id,id),
  CONSTRAINT chk_mail_archive_size CHECK(mime_size_bytes BETWEEN 1 AND 262144)
);
ALTER TABLE email_mailbox_messages
  ADD COLUMN outbound_intent_id BIGINT UNIQUE,
  ADD CONSTRAINT fk_mail_sent_intent_org
    FOREIGN KEY (organization_id,mailbox_id,outbound_intent_id)
    REFERENCES email_mail_outbound_intents(organization_id,mailbox_id,id);
CREATE INDEX idx_mail_sent_history
  ON email_mailbox_messages(organization_id,mailbox_id,id DESC)
  WHERE direction='OUTBOUND' AND folder='SENT' AND deleted_at IS NULL;

ALTER TABLE email_mail_outbound_recipients
  ADD CONSTRAINT uq_mail_outbound_recipient_tenant_id
    UNIQUE (organization_id,mailbox_id,id),
  ADD COLUMN remote_status VARCHAR(24) NOT NULL DEFAULT 'NOT_CONFIRMED',
  ADD COLUMN remote_status_at TIMESTAMPTZ,
  ADD COLUMN remote_status_code VARCHAR(16),
  ADD CONSTRAINT chk_mail_remote_status CHECK(
    remote_status IN ('NOT_CONFIRMED','DEFERRED','DELIVERED','BOUNCED','COMPLAINED'));
-- Feedback is an immutable event history. A replay of the same MTA event has
-- exactly one effect even if multiple workers receive it simultaneously.
CREATE TABLE email_mail_outbound_feedback (
  id BIGINT PRIMARY KEY,
  organization_id BIGINT NOT NULL,
  mailbox_id BIGINT NOT NULL,
  recipient_id BIGINT NOT NULL,
  external_event_id VARCHAR(128) NOT NULL,
  event_type VARCHAR(24) NOT NULL,
  status_code VARCHAR(16),
  occurred_at TIMESTAMPTZ NOT NULL,
  recorded_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT fk_mail_feedback_recipient_org
    FOREIGN KEY (organization_id,mailbox_id,recipient_id)
    REFERENCES email_mail_outbound_recipients(organization_id,mailbox_id,id),
  CONSTRAINT uq_mail_feedback_event UNIQUE (recipient_id,external_event_id),
  CONSTRAINT chk_mail_feedback_type CHECK
    (event_type IN ('DEFERRED','DELIVERED','BOUNCED','COMPLAINED'))
);
CREATE INDEX idx_mail_feedback_recipient
  ON email_mail_outbound_feedback(recipient_id,recorded_at DESC);
CREATE INDEX idx_mail_remote_terminal
  ON email_mail_outbound_recipients(organization_id,remote_status,remote_status_at)
  WHERE remote_status IN ('BOUNCED','COMPLAINED');

-- Snapshot rows are at most 20 recipients per held intent.
CREATE INDEX idx_mail_outbound_ops_recent
  ON email_mail_outbound_recipients(updated_at,state);
