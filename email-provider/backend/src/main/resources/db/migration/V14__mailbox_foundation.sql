-- Mailbox-hosting foundation. This migration is additive and does not alter
-- existing outbound sending domains, projects, quotas, or SES integrations.
-- Inbound SMTP is NOT enabled by this schema.
CREATE TABLE email_mail_domains (
  id BIGINT PRIMARY KEY,
  organization_id BIGINT NOT NULL REFERENCES email_product_organizations(id),
  domain_name VARCHAR(253) NOT NULL UNIQUE,
  status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
  verification_token_hash VARCHAR(64),
  verified_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT chk_email_mail_domain_status CHECK (status IN ('PENDING','VERIFIED','SUSPENDED'))
);
CREATE INDEX idx_email_mail_domains_org ON email_mail_domains(organization_id);

CREATE TABLE email_mailboxes (
  id BIGINT PRIMARY KEY,
  organization_id BIGINT NOT NULL REFERENCES email_product_organizations(id),
  domain_id BIGINT NOT NULL REFERENCES email_mail_domains(id),
  local_part VARCHAR(64) NOT NULL,
  display_name VARCHAR(160),
  status VARCHAR(24) NOT NULL DEFAULT 'DISABLED',
  storage_quota_bytes BIGINT NOT NULL DEFAULT 1073741824,
  storage_used_bytes BIGINT NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT uq_email_mailbox_address UNIQUE (domain_id, local_part),
  CONSTRAINT chk_email_mailbox_status CHECK (status IN ('DISABLED','ACTIVE','SUSPENDED')),
  CONSTRAINT chk_email_mailbox_quota CHECK (storage_quota_bytes >= 0 AND storage_used_bytes >= 0)
);
CREATE INDEX idx_email_mailboxes_org ON email_mailboxes(organization_id);

-- Object store retains RFC 5322 original MIME; no raw MIME blobs in PostgreSQL.
-- message_id_header is not globally unique: duplicates and legitimate re-deliveries exist.
CREATE TABLE email_mailbox_messages (
  id BIGINT PRIMARY KEY,
  organization_id BIGINT NOT NULL REFERENCES email_product_organizations(id),
  mailbox_id BIGINT NOT NULL REFERENCES email_mailboxes(id),
  direction VARCHAR(16) NOT NULL,
  folder VARCHAR(24) NOT NULL,
  message_id_header VARCHAR(998),
  from_address VARCHAR(320),
  subject VARCHAR(998),
  received_at TIMESTAMPTZ,
  sent_at TIMESTAMPTZ,
  mime_object_key VARCHAR(1024) NOT NULL,
  mime_size_bytes BIGINT NOT NULL,
  is_read BOOLEAN NOT NULL DEFAULT FALSE,
  is_starred BOOLEAN NOT NULL DEFAULT FALSE,
  deleted_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT chk_email_mail_direction CHECK (direction IN ('INBOUND','OUTBOUND')),
  CONSTRAINT chk_email_mail_folder CHECK (folder IN ('INBOX','SENT','DRAFTS','TRASH','SPAM','ARCHIVE')),
  CONSTRAINT chk_email_mail_mime_size CHECK (mime_size_bytes >= 0)
);
CREATE INDEX idx_email_mail_messages_listing ON email_mailbox_messages(mailbox_id,folder,received_at DESC,id DESC);
CREATE INDEX idx_email_mail_messages_org ON email_mailbox_messages(organization_id);

-- Real user identities, org/domain authorization, object encryption,
-- mailbox provisioning and delivery idempotency are required before writes.
