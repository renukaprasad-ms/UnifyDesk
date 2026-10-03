-- Billing R1 B2 final remediation: durable provider orchestration, recurring authorization,
-- restart-safe claims, and refund/payment recovery. B1 commercial-core tables are unchanged.

alter table payments
  add column provider_order_state varchar(32) not null default 'CREATE_PENDING',
  add column provider_order_status varchar(64),
  add column provider_checkout_session_id varchar(2048),
  add column lifecycle_applied_at timestamp(6) with time zone;

update payments
   set lifecycle_applied_at = coalesce(succeeded_at, updated_at)
 where status = 'SUCCEEDED';

alter table payment_attempts
  add column provider_command_id varchar(255),
  add column provider_submission_state varchar(32) not null default 'NOT_SUBMITTED';

create unique index uk_payment_attempts_provider_command
  on payment_attempts(provider_command_id)
  where provider_command_id is not null;

alter table refunds
  add column provider_refund_reference varchar(255),
  add column provider_operation_state varchar(32) not null default 'CREATE_PENDING',
  add column provider_created_at timestamp(6) with time zone,
  add column provider_processed_at timestamp(6) with time zone;

create unique index uk_refunds_provider_reference
  on refunds(provider_refund_reference)
  where provider_refund_reference is not null;
create index idx_refunds_reconcile
  on refunds(status, updated_at)
  where status in ('CREATED', 'PENDING');

alter table payment_provider_events
  add column signature_verified_at timestamp(6) with time zone,
  add column claim_token varchar(64),
  add column claimed_until timestamp(6) with time zone;

create index idx_payment_provider_events_claim
  on payment_provider_events(processing_status, next_attempt_at, claimed_until);

alter table payment_dunning_jobs
  add column claim_token varchar(64),
  add column claimed_until timestamp(6) with time zone;

create table payment_provider_authorizations (
  id bigint primary key,
  company_id bigint not null,
  subscription_id bigint not null,
  provider varchar(32) not null,
  provider_subscription_id varchar(255) not null,
  provider_subscription_session_id varchar(2048),
  authorization_payment_id varchar(255) not null,
  provider_authorization_payment_id varchar(255),
  authorization_status varchar(64) not null,
  provider_status varchar(64),
  max_amount numeric(19, 6) not null,
  currency varchar(3) not null,
  provider_created_at timestamp(6) with time zone,
  provider_updated_at timestamp(6) with time zone,
  retention_until timestamp(6) with time zone not null default (now() + interval '8 years'),
  legal_hold boolean not null default false,
  created_at timestamp(6) with time zone not null,
  updated_at timestamp(6) with time zone not null,
  constraint uk_payment_provider_authorization_subscription
    unique(company_id, subscription_id, provider),
  constraint uk_payment_provider_authorization_provider
    unique(provider, provider_subscription_id),
  constraint uk_payment_provider_authorization_auth_payment
    unique(provider, authorization_payment_id),
  constraint fk_payment_provider_authorizations_company
    foreign key(company_id) references companies(id),
  constraint ck_payment_provider_authorizations_currency
    check(currency = 'INR')
);

create index idx_payment_provider_authorizations_status
  on payment_provider_authorizations(provider, authorization_status, updated_at);
