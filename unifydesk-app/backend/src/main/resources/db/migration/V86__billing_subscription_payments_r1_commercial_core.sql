-- UnifyDesk Billing, Subscription & Payments R1 — B1 commercial core.
-- Provider/payment-rail objects are intentionally excluded from B1.

alter table plans
  add column if not exists visibility varchar(32) not null default 'PUBLIC';

alter table plans drop constraint if exists ck_plans_visibility;
alter table plans add constraint ck_plans_visibility
  check (visibility in ('PUBLIC', 'PRIVATE', 'INTERNAL'));

alter table company_subscriptions add column if not exists billing_anchor timestamp(6) with time zone;
alter table company_subscriptions add column if not exists grace_ends_at timestamp(6) with time zone;
alter table company_subscriptions add column if not exists payment_policy varchar(32);
alter table company_subscriptions add column if not exists external_contract_reference varchar(255);
alter table company_subscriptions add column if not exists cancellation_effective_at timestamp(6) with time zone;
alter table company_subscriptions add column if not exists retention_until timestamp(6) with time zone
  default (now() + interval '8 years');
alter table company_subscriptions add column if not exists legal_hold boolean not null default false;

update company_subscriptions
set retention_until = coalesce(retention_until, created_at + interval '8 years');

alter table company_subscriptions alter column retention_until set not null;

drop index if exists uk_company_subscriptions_current;
create unique index if not exists uk_company_subscriptions_current
  on company_subscriptions (company_id)
  where status in ('ACTIVE', 'TRIALING', 'PAST_DUE', 'SUSPENDED');

create table if not exists plan_company_assignments (
  id bigint primary key,
  plan_id bigint not null,
  company_id bigint not null,
  assigned_at timestamp(6) with time zone not null,
  assigned_by_user_id bigint not null,
  effective_from timestamp(6) with time zone not null,
  effective_to timestamp(6) with time zone,
  revoked_at timestamp(6) with time zone,
  revoked_by_user_id bigint,
  reason varchar(500),
  constraint fk_plan_company_assignments_plan foreign key (plan_id) references plans(id),
  constraint fk_plan_company_assignments_company foreign key (company_id) references companies(id),
  constraint ck_plan_company_assignments_period check (effective_to is null or effective_to > effective_from)
);
create index if not exists idx_plan_company_assignments_lookup
  on plan_company_assignments (company_id, plan_id, effective_from, effective_to, revoked_at);

create table if not exists subscription_transitions (
  id bigint primary key,
  company_id bigint not null,
  subscription_id bigint not null,
  from_plan_id bigint,
  to_plan_id bigint,
  from_status varchar(32),
  to_status varchar(32) not null,
  transition_type varchar(64) not null,
  actor_user_id bigint,
  reason varchar(1000),
  effective_from timestamp(6) with time zone,
  effective_to timestamp(6) with time zone,
  payment_policy varchar(32),
  external_reference varchar(255),
  occurred_at timestamp(6) with time zone not null,
  retention_until timestamp(6) with time zone not null default (now() + interval '8 years'),
  legal_hold boolean not null default false,
  constraint fk_subscription_transitions_company foreign key (company_id) references companies(id),
  constraint fk_subscription_transitions_subscription foreign key (subscription_id) references company_subscriptions(id),
  constraint fk_subscription_transitions_from_plan foreign key (from_plan_id) references plans(id),
  constraint fk_subscription_transitions_to_plan foreign key (to_plan_id) references plans(id)
);
create index if not exists idx_subscription_transitions_company_time
  on subscription_transitions (company_id, occurred_at desc);
create index if not exists idx_subscription_transitions_subscription
  on subscription_transitions (subscription_id, occurred_at desc);

create table if not exists subscription_changes (
  id bigint primary key,
  company_id bigint not null,
  subscription_id bigint not null,
  from_plan_id bigint not null,
  to_plan_id bigint not null,
  change_type varchar(32) not null,
  status varchar(32) not null,
  requested_at timestamp(6) with time zone not null,
  requested_by_user_id bigint,
  effective_at timestamp(6) with time zone not null,
  quote_reference varchar(160),
  invoice_id bigint,
  idempotency_key varchar(255) not null,
  currency varchar(3) not null,
  current_price_snapshot numeric(19, 6),
  target_price_snapshot numeric(19, 6),
  current_period_start timestamp(6) with time zone,
  current_period_end timestamp(6) with time zone,
  target_prorated_charge numeric(19, 6),
  unused_current_credit numeric(19, 6),
  net_amount numeric(19, 6),
  amount_due numeric(19, 6),
  credit_amount numeric(19, 6),
  applied_at timestamp(6) with time zone,
  cancelled_at timestamp(6) with time zone,
  created_at timestamp(6) with time zone not null,
  updated_at timestamp(6) with time zone not null,
  retention_until timestamp(6) with time zone not null default (now() + interval '8 years'),
  legal_hold boolean not null default false,
  constraint uk_subscription_changes_idempotency unique (idempotency_key),
  constraint fk_subscription_changes_company foreign key (company_id) references companies(id),
  constraint fk_subscription_changes_subscription foreign key (subscription_id) references company_subscriptions(id),
  constraint fk_subscription_changes_from_plan foreign key (from_plan_id) references plans(id),
  constraint fk_subscription_changes_to_plan foreign key (to_plan_id) references plans(id),
  constraint ck_subscription_changes_currency check (currency = 'INR')
);
create index if not exists idx_subscription_changes_due
  on subscription_changes (status, effective_at);
create index if not exists idx_subscription_changes_company
  on subscription_changes (company_id, requested_at desc);

create table if not exists billing_periods (
  id bigint primary key,
  company_id bigint not null,
  kind varchar(32) not null,
  cadence varchar(32) not null,
  start_at timestamp(6) with time zone not null,
  end_at timestamp(6) with time zone not null,
  status varchar(32) not null,
  finalized_at timestamp(6) with time zone,
  created_at timestamp(6) with time zone not null,
  updated_at timestamp(6) with time zone not null,
  retention_until timestamp(6) with time zone not null default (now() + interval '8 years'),
  legal_hold boolean not null default false,
  constraint uk_billing_period_company_kind_range unique (company_id, kind, start_at, end_at),
  constraint fk_billing_period_company foreign key (company_id) references companies(id),
  constraint ck_billing_period_range check (end_at > start_at)
);
create index if not exists idx_billing_period_due on billing_periods (status, end_at);
create index if not exists idx_billing_period_company on billing_periods (company_id, kind, start_at desc);

create table if not exists invoices (
  id bigint primary key,
  company_id bigint not null,
  billing_period_id bigint,
  invoice_number varchar(80) not null,
  status varchar(32) not null,
  currency varchar(3) not null,
  company_name_snapshot varchar(160) not null,
  business_email_snapshot varchar(254),
  billing_address_line1 varchar(255),
  billing_address_line2 varchar(255),
  billing_city varchar(120),
  billing_state varchar(120),
  billing_postal_code varchar(32),
  billing_country varchar(80),
  tax_id_snapshot varchar(120),
  tax_registration_snapshot varchar(120),
  subtotal numeric(19, 6) not null,
  discount_amount numeric(19, 6) not null,
  credit_amount numeric(19, 6) not null,
  adjustment_amount numeric(19, 6) not null,
  tax_amount numeric(19, 6) not null,
  total numeric(19, 6) not null,
  amount_due numeric(19, 6) not null,
  service_period_start timestamp(6) with time zone,
  service_period_end timestamp(6) with time zone,
  finalized_at timestamp(6) with time zone,
  due_at timestamp(6) with time zone,
  paid_at timestamp(6) with time zone,
  created_at timestamp(6) with time zone not null,
  updated_at timestamp(6) with time zone not null,
  retention_until timestamp(6) with time zone not null default (now() + interval '8 years'),
  legal_hold boolean not null default false,
  constraint uk_invoices_number unique (invoice_number),
  constraint fk_invoices_company foreign key (company_id) references companies(id),
  constraint fk_invoices_period foreign key (billing_period_id) references billing_periods(id),
  constraint ck_invoices_currency check (currency = 'INR')
);
create index if not exists idx_invoices_company_time on invoices (company_id, created_at desc);
create index if not exists idx_invoices_period on invoices (billing_period_id);

create table if not exists invoice_line_items (
  id bigint primary key,
  invoice_id bigint not null,
  line_type varchar(64) not null,
  description varchar(500) not null,
  quantity numeric(19, 6) not null,
  unit_amount numeric(19, 6) not null,
  amount numeric(19, 6) not null,
  currency varchar(3) not null,
  service_period_start timestamp(6) with time zone,
  service_period_end timestamp(6) with time zone,
  original_service_period_start timestamp(6) with time zone,
  original_service_period_end timestamp(6) with time zone,
  source_reference varchar(255),
  created_at timestamp(6) with time zone not null,
  retention_until timestamp(6) with time zone not null default (now() + interval '8 years'),
  legal_hold boolean not null default false,
  constraint fk_invoice_line_items_invoice foreign key (invoice_id) references invoices(id),
  constraint ck_invoice_line_items_currency check (currency = 'INR')
);
create index if not exists idx_invoice_line_items_invoice on invoice_line_items (invoice_id);

alter table subscription_changes
  add constraint fk_subscription_changes_invoice foreign key (invoice_id) references invoices(id);
