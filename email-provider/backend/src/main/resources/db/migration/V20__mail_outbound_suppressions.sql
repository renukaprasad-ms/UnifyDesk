-- R9: mailbox-level outbound suppressions, independent of marketing projects.
-- A hard bounce or complaint prevents future mailbox handoffs for the tenant.
CREATE TABLE email_mail_outbound_suppressions (
  organization_id BIGINT NOT NULL REFERENCES email_product_organizations(id),
  recipient_address VARCHAR(320) NOT NULL,
  reason VARCHAR(24) NOT NULL,
  first_recipient_id BIGINT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (organization_id,recipient_address),
  CONSTRAINT chk_mail_suppression_reason CHECK(reason IN ('HARD_BOUNCE','COMPLAINT'))
);
CREATE INDEX idx_mail_suppression_recency
  ON email_mail_outbound_suppressions(organization_id,created_at DESC);
