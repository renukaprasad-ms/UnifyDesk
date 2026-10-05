-- UnifyDesk Billing R1 B2 provider-neutral payment/refund and durable provider-event truth.

create table payments (
  id bigint primary key,
  company_id bigint not null,
  invoice_id bigint not null,
  purpose varchar(32) not null,
  status varchar(32) not null,
  amount numeric(19, 6) not null,
  currency varchar(3) not null,
  idempotency_key varchar(255) not null,
  provider varchar(32) not null,
  provider_order_id varchar(255),
  provider_subscription_id varchar(255),
  checkout_intent_id bigint,
  subscription_change_id bigint,
  subscription_id bigint,
  succeeded_at timestamp(6) with time zone,
  version bigint not null default 0,
  retention_until timestamp(6) with time zone not null default (now() + interval '8 years'),
  legal_hold boolean not null default false,
  created_at timestamp(6) with time zone not null,
  updated_at timestamp(6) with time zone not null,
  constraint uk_payments_invoice unique (invoice_id),
  constraint uk_payments_company_idempotency unique (company_id, idempotency_key),
  constraint fk_payments_company foreign key (company_id) references companies(id),
  constraint fk_payments_invoice foreign key (invoice_id) references invoices(id),
  constraint ck_payments_currency check (currency = 'INR')
);
create unique index uk_payments_provider_order
  on payments(provider, provider_order_id) where provider_order_id is not null;
create index idx_payments_status on payments(status, updated_at);

create table payment_attempts (
  id bigint primary key,
  payment_id bigint not null,
  attempt_no integer not null,
  status varchar(32) not null,
  amount numeric(19, 6) not null,
  currency varchar(3) not null,
  provider_payment_id varchar(255),
  failure_category varchar(80),
  provider_created_at timestamp(6) with time zone,
  resolved_at timestamp(6) with time zone,
  retention_until timestamp(6) with time zone not null default (now() + interval '8 years'),
  legal_hold boolean not null default false,
  created_at timestamp(6) with time zone not null,
  constraint uk_payment_attempts_payment_number unique(payment_id, attempt_no),
  constraint fk_payment_attempts_payment foreign key(payment_id) references payments(id),
  constraint ck_payment_attempts_currency check (currency = 'INR')
);
create unique index uk_payment_attempts_provider_payment
  on payment_attempts(provider_payment_id) where provider_payment_id is not null;
create index idx_payment_attempts_payment on payment_attempts(payment_id, attempt_no);

create table refunds (
  id bigint primary key,
  company_id bigint not null,
  payment_id bigint not null,
  status varchar(32) not null,
  amount numeric(19, 6) not null,
  currency varchar(3) not null,
  idempotency_key varchar(255) not null,
  provider_refund_id varchar(255),
  provider_status varchar(64),
  retention_until timestamp(6) with time zone not null default (now() + interval '8 years'),
  legal_hold boolean not null default false,
  created_at timestamp(6) with time zone not null,
  updated_at timestamp(6) with time zone not null,
  constraint uk_refunds_company_idempotency unique(company_id, idempotency_key),
  constraint fk_refunds_payment foreign key(payment_id) references payments(id),
  constraint ck_refunds_currency check (currency = 'INR')
);
create unique index uk_refunds_provider_ref
  on refunds(provider_refund_id) where provider_refund_id is not null;

create table payment_provider_events (
  id bigint primary key,
  provider varchar(32) not null,
  provider_event_key varchar(255) not null,
  provider_object_id varchar(255),
  event_type varchar(96),
  payload_hash varchar(64) not null,
  raw_payload text not null,
  provider_created_at timestamp(6) with time zone,
  received_at timestamp(6) with time zone not null,
  processing_status varchar(32) not null,
  attempt_count integer not null default 0,
  next_attempt_at timestamp(6) with time zone,
  last_error_category varchar(80),
  payment_id bigint,
  refund_id bigint,
  retention_until timestamp(6) with time zone not null default (now() + interval '8 years'),
  legal_hold boolean not null default false,
  constraint uk_payment_provider_events_key unique(provider, provider_event_key),
  constraint fk_payment_provider_events_payment foreign key(payment_id) references payments(id),
  constraint fk_payment_provider_events_refund foreign key(refund_id) references refunds(id)
);
create index idx_payment_provider_events_retry
  on payment_provider_events(processing_status, next_attempt_at);

create table payment_dunning_jobs (
  id bigint primary key,
  company_id bigint not null,
  subscription_id bigint not null,
  invoice_id bigint not null,
  payment_id bigint not null,
  status varchar(32) not null,
  attempt_count integer not null default 0,
  next_attempt_at timestamp(6) with time zone,
  grace_ends_at timestamp(6) with time zone not null,
  created_at timestamp(6) with time zone not null,
  updated_at timestamp(6) with time zone not null,
  constraint uk_payment_dunning_invoice unique(invoice_id),
  constraint fk_payment_dunning_company foreign key(company_id) references companies(id),
  constraint fk_payment_dunning_invoice foreign key(invoice_id) references invoices(id),
  constraint fk_payment_dunning_payment foreign key(payment_id) references payments(id)
);
create index idx_payment_dunning_due on payment_dunning_jobs(status, next_attempt_at);
