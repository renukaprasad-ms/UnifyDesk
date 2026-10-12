-- Billing V2 reporting indexes for cross-company Super Admin date-window aggregations.
-- Keep each reporting source indexed by event time so the dashboard does not scan full
-- historical provider/event tables on every daily or weekly refresh.

create index if not exists idx_billing_v2_managed_usage_window
  on usage_events (occurred_at, company_id)
  where service_ownership = 'MANAGED' and currency = 'INR';

create index if not exists idx_billing_v2_managed_email_sent_window
  on managed_email_recipient_delivery (sent_event_at, company_id)
  where sent_event_at is not null;

create index if not exists idx_billing_v2_managed_sms_accepted_window
  on managed_sms_recipient_delivery (accepted_at, company_id)
  where accepted_at is not null;

create index if not exists idx_billing_v2_whatsapp_runtime_window
  on meta_whatsapp_message_runtime (created_at, company_id)
  where detailed_status in ('ACCEPTED', 'SENT', 'DELIVERED', 'READ');

create index if not exists idx_billing_v2_inbox_message_window
  on inbox_messages ((coalesce(sent_at, created_at)), connection_id)
  where status in ('RECEIVED', 'SENT');

create index if not exists idx_billing_v2_invoice_finalized_window
  on invoices (finalized_at, company_id)
  where status in ('OPEN', 'PAID', 'UNCOLLECTIBLE') and currency = 'INR';

create index if not exists idx_billing_v2_open_invoice_balance
  on invoices (company_id)
  where status = 'OPEN' and currency = 'INR';

create index if not exists idx_billing_v2_successful_payments_window
  on payments (succeeded_at, company_id)
  where status = 'SUCCEEDED' and currency = 'INR';

create index if not exists idx_billing_v2_successful_refunds_window
  on refunds ((coalesce(provider_processed_at, updated_at)), company_id)
  where status = 'SUCCEEDED' and currency = 'INR';
