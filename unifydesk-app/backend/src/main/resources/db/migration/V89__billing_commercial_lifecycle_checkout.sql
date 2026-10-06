-- Billing R1 B1 commercial-lifecycle remediation.
-- Durable provider-neutral identity for the pre-payment initial subscription invoice.

create table subscription_checkout_intents (
  id bigint primary key,
  company_id bigint not null,
  plan_id bigint not null,
  plan_price_id bigint not null,
  billing_period_id bigint not null,
  invoice_id bigint,
  subscription_id bigint,
  status varchar(32) not null,
  idempotency_key varchar(255) not null,
  currency varchar(3) not null,
  amount_snapshot numeric(19, 6) not null,
  service_period_start timestamp(6) with time zone not null,
  service_period_end timestamp(6) with time zone not null,
  requested_at timestamp(6) with time zone not null,
  requested_by_user_id bigint,
  activated_at timestamp(6) with time zone,
  external_reference varchar(255),
  retention_until timestamp(6) with time zone not null default (now() + interval '8 years'),
  legal_hold boolean not null default false,
  created_at timestamp(6) with time zone not null,
  updated_at timestamp(6) with time zone not null,
  constraint uk_subscription_checkout_intents_company_idempotency
    unique (company_id, idempotency_key),
  constraint uk_subscription_checkout_intents_billing_period
    unique (billing_period_id),
  constraint uk_subscription_checkout_intents_invoice
    unique (invoice_id),
  constraint uk_subscription_checkout_intents_subscription
    unique (subscription_id),
  constraint fk_subscription_checkout_intents_company
    foreign key (company_id) references companies(id),
  constraint fk_subscription_checkout_intents_plan
    foreign key (plan_id) references plans(id),
  constraint fk_subscription_checkout_intents_plan_price
    foreign key (plan_price_id) references plan_prices(id),
  constraint fk_subscription_checkout_intents_billing_period
    foreign key (billing_period_id) references billing_periods(id),
  constraint fk_subscription_checkout_intents_invoice
    foreign key (invoice_id) references invoices(id),
  constraint fk_subscription_checkout_intents_subscription
    foreign key (subscription_id) references company_subscriptions(id),
  constraint ck_subscription_checkout_intents_currency check (currency = 'INR'),
  constraint ck_subscription_checkout_intents_range
    check (service_period_end > service_period_start)
);

create index idx_subscription_checkout_intents_company_status
  on subscription_checkout_intents (company_id, status);
create index idx_subscription_checkout_intents_invoice
  on subscription_checkout_intents (invoice_id);
