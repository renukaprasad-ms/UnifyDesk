-- Every locally accepted Postfix handoff is one ledger line. These are
-- delivery-accepted units, NOT final inbox delivery, and NOT captured payments.
CREATE TABLE email_product_rates (
 project_id BIGINT PRIMARY KEY REFERENCES email_product_projects(id),
 price_micro_inr_per_recipient BIGINT NOT NULL DEFAULT 0
   CHECK (price_micro_inr_per_recipient >= 0),
 updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE email_product_usage_entries (
 id BIGINT PRIMARY KEY,
 recipient_id BIGINT NOT NULL UNIQUE REFERENCES email_recipients(id),
 project_id BIGINT NOT NULL REFERENCES email_product_projects(id),
 message_id BIGINT NOT NULL REFERENCES email_messages(id),
 unit_price_micro_inr BIGINT NOT NULL CHECK (unit_price_micro_inr >= 0),
 occurred_at TIMESTAMPTZ NOT NULL,
 created_at TIMESTAMPTZ NOT NULL,
 updated_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_email_product_usage_project_date
 ON email_product_usage_entries(project_id,occurred_at);
