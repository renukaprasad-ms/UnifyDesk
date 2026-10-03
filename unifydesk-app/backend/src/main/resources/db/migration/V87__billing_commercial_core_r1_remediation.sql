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

with matched_platform_periods as (
  select
    bp.id as billing_period_id,
    s.id as subscription_id,
    s.plan_id,
    s.plan_price_id,
    coalesce(pp.amount, p.base_price) as amount_snapshot,
    coalesce(pp.currency, p.currency) as currency_snapshot,
    row_number() over (
      partition by bp.id
      order by s.started_at desc, s.id desc
    ) as rn
  from billing_periods bp
  join company_subscriptions s
    on s.company_id = bp.company_id
   and s.current_period_start = bp.start_at
   and s.current_period_end = bp.end_at
  join plans p on p.id = s.plan_id
  left join plan_prices pp on pp.id = s.plan_price_id
  where bp.kind = 'PLATFORM'
)
update billing_periods bp
set subscription_id = matched.subscription_id,
    plan_id = matched.plan_id,
    plan_price_id = matched.plan_price_id,
    platform_amount_snapshot = matched.amount_snapshot,
    currency_snapshot = matched.currency_snapshot
from matched_platform_periods matched
where bp.id = matched.billing_period_id
  and matched.rn = 1
  and bp.subscription_id is null;

create index idx_billing_period_closing
  on billing_periods (status, end_at, id);
