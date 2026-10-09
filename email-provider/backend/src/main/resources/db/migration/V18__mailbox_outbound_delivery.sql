-- R7 mailbox transport: independent authorization, per-recipient attempts, and
-- explicit SMTP handoff states. No work is performed unless outbound is enabled.
ALTER TABLE email_mail_domains ADD COLUMN outbound_enabled BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE email_mail_outbound_intents DROP CONSTRAINT chk_mail_outbound_state;
ALTER TABLE email_mail_outbound_intents ADD CONSTRAINT chk_mail_outbound_state CHECK
  (state IN ('HELD','PROCESSING','HANDOFF_COMPLETED','PARTIAL','UNKNOWN','FAILED'));
ALTER TABLE email_mail_outbound_intents ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT now();

CREATE TABLE email_mail_outbound_recipients (
  id BIGINT PRIMARY KEY,
  organization_id BIGINT NOT NULL,
  mailbox_id BIGINT NOT NULL,
  intent_id BIGINT NOT NULL REFERENCES email_mail_outbound_intents(id),
  recipient_address VARCHAR(320) NOT NULL,
  recipient_type VARCHAR(3) NOT NULL,
  state VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  attempts INT NOT NULL DEFAULT 0,
  claim_token UUID,
  claimed_at TIMESTAMPTZ,
  retry_after TIMESTAMPTZ,
  last_smtp_code VARCHAR(8),
  last_error VARCHAR(160),
  handed_off_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT fk_mail_outbound_recipient_tenant
    FOREIGN KEY (organization_id,mailbox_id)
    REFERENCES email_mailboxes(organization_id,id),
  CONSTRAINT uq_mail_intent_address UNIQUE(intent_id,recipient_address),
  CONSTRAINT chk_mail_outbound_recipient_type
    CHECK (recipient_type IN ('TO','CC','BCC')),
  CONSTRAINT chk_mail_outbound_recipient_state
    CHECK (state IN ('PENDING','PROCESSING','DEFERRED','HANDED_OFF','UNKNOWN','FAILED')),
  CONSTRAINT chk_mail_outbound_recipient_attempts CHECK(attempts BETWEEN 0 AND 3),
  CONSTRAINT chk_mail_outbound_claim
    CHECK ((state='PROCESSING' AND claim_token IS NOT NULL AND claimed_at IS NOT NULL)
       OR (state<>'PROCESSING' AND claim_token IS NULL AND claimed_at IS NULL))
);
CREATE INDEX idx_mail_outbound_ready
  ON email_mail_outbound_recipients(retry_after,id)
  WHERE state IN ('PENDING','DEFERRED');
CREATE INDEX idx_mail_outbound_stale
  ON email_mail_outbound_recipients(claimed_at)
  WHERE state='PROCESSING';
CREATE INDEX idx_mail_outbound_intent_status
  ON email_mail_outbound_recipients(intent_id,state);
