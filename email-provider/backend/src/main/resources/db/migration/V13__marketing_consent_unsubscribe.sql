ALTER TABLE email_messages
  ADD COLUMN marketing BOOLEAN NOT NULL DEFAULT FALSE,
  ADD COLUMN consent_reference VARCHAR(128);
CREATE INDEX idx_email_messages_marketing ON email_messages(marketing);
