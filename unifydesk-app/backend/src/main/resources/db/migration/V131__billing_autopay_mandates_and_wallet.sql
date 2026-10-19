-- Billing AutoPay Mandates and Wallet Auto-Recharge Retry Policy

-- 1. Allow standalone company-level autopay in payment_provider_authorizations
alter table payment_provider_authorizations
  add column is_company_autopay boolean not null default false,
  add column payment_method_type varchar(64),
  add column expires_at timestamp(6) with time zone;

alter table payment_provider_authorizations
  drop constraint if exists ck_payment_provider_authorization_binding;

alter table payment_provider_authorizations
  add constraint ck_payment_provider_authorization_binding
  check (subscription_id is not null or checkout_intent_id is not null or is_company_autopay = true);

create unique index if not exists uk_payment_provider_authorization_company_autopay
  on payment_provider_authorizations(company_id, provider)
  where is_company_autopay = true;

-- 2. Wallet auto-recharge retry & pausing columns
alter table company_wallets
  add column if not exists auto_recharge_failed_attempts integer not null default 0,
  add column if not exists auto_recharge_last_attempt_at timestamp(6) with time zone,
  add column if not exists auto_recharge_paused boolean not null default false;
