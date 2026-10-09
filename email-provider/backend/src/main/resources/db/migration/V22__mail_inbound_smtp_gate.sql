-- R10: explicit operator approval for external MX/SMTP receiving.
-- Verified ownership and an ACTIVE mailbox are NOT by themselves consent to
-- route Internet email here. Start every existing and new tenant disabled.
ALTER TABLE email_mail_domains
  ADD COLUMN inbound_enabled BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN email_mail_domains.inbound_enabled IS
  'Operator-controlled SMTP inbound admission gate; never set by DNS verification';
