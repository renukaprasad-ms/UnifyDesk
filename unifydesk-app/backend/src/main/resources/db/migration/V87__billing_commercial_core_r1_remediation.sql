-- Billing R1 B1 remediation: tenant-safe idempotency and financial uniqueness.

alter table subscription_changes
  drop constraint if exists uk_subscription_changes_idempotency;

alter table subscription_changes
  add constraint uk_subscription_changes_company_idempotency
  unique (company_id, idempotency_key);

alter table invoices
  add constraint uk_invoices_billing_period
  unique (billing_period_id);

alter table invoice_line_items
  add column idempotency_key varchar(255);

update invoice_line_items
set idempotency_key = coalesce(nullif(source_reference, ''), 'LEGACY:' || id::text)
where idempotency_key is null;

alter table invoice_line_items
  alter column idempotency_key set not null;

alter table invoice_line_items
  add constraint uk_invoice_line_items_idempotency
  unique (invoice_id, idempotency_key);

alter table billing_periods
  add column subscription_id bigint,
  add column plan_id bigint,
  add column plan_price_id bigint,
  add column platform_amount_snapshot numeric(19, 6),
  add column currency_snapshot varchar(3);

alter table billing_periods
  add constraint fk_billing_period_subscription
    foreign key (subscription_id) references company_subscriptions(id),
  add constraint fk_billing_period_plan
    foreign key (plan_id) references plans(id),
  add constraint fk_billing_period_plan_price
    foreign key (plan_price_id) references plan_prices(id);

create index idx_billing_period_closing
  on billing_periods (status, end_at, id);
