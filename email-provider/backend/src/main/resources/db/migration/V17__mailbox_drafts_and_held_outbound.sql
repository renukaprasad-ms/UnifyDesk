-- R6: mailbox-specific draft state and held outgoing submission intent.
-- Does not reuse marketing projects, public API keys or SES credentials.
-- Nothing in this table is eligible for SMTP until an audited transport is added.
CREATE TABLE email_mail_drafts (
  id BIGINT PRIMARY KEY,
  organization_id BIGINT NOT NULL,
  mailbox_id BIGINT NOT NULL,
  author_user_id BIGINT NOT NULL,
  reply_to_message_id BIGINT,
  recipients_to TEXT NOT NULL DEFAULT '',
  recipients_cc TEXT NOT NULL DEFAULT '',
  recipients_bcc TEXT NOT NULL DEFAULT '',
  subject VARCHAR(998) NOT NULL DEFAULT '',
  body_text TEXT NOT NULL DEFAULT '',
  status VARCHAR(24) NOT NULL DEFAULT 'DRAFT',
  revision BIGINT NOT NULL DEFAULT 1,
  submission_key VARCHAR(128),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  submitted_at TIMESTAMPTZ,
  CONSTRAINT fk_mail_draft_mailbox FOREIGN KEY (organization_id,mailbox_id)
    REFERENCES email_mailboxes (organization_id,id),
  CONSTRAINT fk_mail_draft_user FOREIGN KEY (organization_id,author_user_id)
    REFERENCES email_console_users (organization_id,id),
  CONSTRAINT fk_mail_draft_reply FOREIGN KEY (reply_to_message_id)
    REFERENCES email_mailbox_messages(id),
  CONSTRAINT chk_mail_draft_status CHECK (status IN ('DRAFT','PENDING_TRANSPORT')),
  CONSTRAINT chk_mail_draft_revision CHECK (revision > 0),
  CONSTRAINT chk_mail_draft_body_bytes CHECK (octet_length(body_text) <= 65536),
  CONSTRAINT chk_mail_draft_recipients_bytes CHECK (
    octet_length(recipients_to) <= 6400 AND
    octet_length(recipients_cc) <= 6400 AND
    octet_length(recipients_bcc) <= 6400)
);
CREATE INDEX idx_mail_drafts_author
  ON email_mail_drafts (organization_id,mailbox_id,author_user_id,id DESC);
CREATE UNIQUE INDEX uq_mail_draft_submission
  ON email_mail_drafts(mailbox_id,submission_key)
  WHERE submission_key IS NOT NULL;

-- Separate durable intent deliberately has no dispatch worker in R6.
CREATE TABLE email_mail_outbound_intents (
  id BIGINT PRIMARY KEY,
  organization_id BIGINT NOT NULL,
  mailbox_id BIGINT NOT NULL,
  draft_id BIGINT NOT NULL UNIQUE REFERENCES email_mail_drafts(id),
  state VARCHAR(24) NOT NULL DEFAULT 'HELD',
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT fk_mail_outbound_mailbox FOREIGN KEY (organization_id,mailbox_id)
    REFERENCES email_mailboxes(organization_id,id),
  CONSTRAINT chk_mail_outbound_state CHECK (state='HELD')
);
CREATE INDEX idx_mail_outbound_held ON email_mail_outbound_intents(created_at,id)
  WHERE state='HELD';
