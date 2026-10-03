-- Billing R1 B2 LAST remediation: automatic renewal orchestration, explicit dunning
-- stages, and recurring-authorization pre-binding. B1 commercial tables/policies are unchanged.

alter table payment_provider_authorizations
  add column checkout_intent_id bigint;

alter table payment_provider_authorizations
  alter column subscription_id drop not null;

alter table payment_provider_authorizations
  drop constraint if exists uk_payment_provider_authorization_subscription;

create unique index uk_payment_provider_authorization_subscription_bound
  on payment_provider_authorizations(company_id, subscription_id, provider)
  where subscription_id is not null;

create unique index uk_payment_provider_authorization_checkout_intent
  on payment_provider_authorizations(company_id, checkout_intent_id, provider)
  where checkout_intent_id is not null;

alter table payment_provider_authorizations
  add constraint ck_payment_provider_authorization_binding
  check (subscription_id is not null or checkout_intent_id is not null);

alter table payment_dunning_jobs
  add column dunning_stage varchar(32);

update payment_dunning_jobs
   set dunning_stage =
         case
           when attempt_count <= 1 then 'DUE_FAILED'
           when attempt_count = 2 then 'D2_FAILED'
           else 'D5_FAILED'
         end,
       next_attempt_at =
         case
           when attempt_count = 2
             and status in ('ACTIVE', 'CLAIMED')
             then least(next_attempt_at, grace_ends_at - interval '2 days')
           else next_attempt_at
         end;

alter table payment_dunning_jobs
  alter column dunning_stage set not null;

create table payment_renewal_jobs (
  id bigint primary key,
  company_id bigint not null,
  subscription_id bigint not null,
  due_boundary timestamp(6) with time zone not null,
  status varchar(32) not null,
  invoice_id bigint,
  payment_id bigint,
  attempt_count integer not null default 0,
  next_attempt_at timestamp(6) with time zone,
  last_error_category varchar(80),
  claim_token varchar(64),
  claimed_until timestamp(6) with time zone,
  created_at timestamp(6) with time zone not null,
  updated_at timestamp(6) with time zone not null,
  constraint uk_payment_renewal_job_boundary unique(subscription_id, due_boundary),
  constraint fk_payment_renewal_jobs_company foreign key(company_id) references companies(id),
  constraint fk_payment_renewal_jobs_invoice foreign key(invoice_id) references invoices(id),
  constraint fk_payment_renewal_jobs_payment foreign key(payment_id) references payments(id)
);

create index idx_payment_renewal_jobs_due
  on payment_renewal_jobs(status, next_attempt_at, claimed_until);
