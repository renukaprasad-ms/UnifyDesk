-- Durable wallet checkout ownership and exactly-once credit boundary.
-- Existing wallet_transactions and historical Flyway migrations are preserved.
create table if not exists wallet_topup_intents (
  id bigint primary key,
  company_id bigint not null references companies(id),
  internal_order_id varchar(128) not null unique,
  provider_order_id varchar(128) unique,
  amount numeric(19,4) not null check (amount > 0),
  currency varchar(3) not null check (currency = 'INR'),
  status varchar(24) not null default 'CREATE_PENDING',
  provider_payment_id varchar(128),
  created_at timestamp(6) with time zone not null default now(),
  credited_at timestamp(6) with time zone,
  constraint ck_wallet_topup_intent_status check (status in ('CREATE_PENDING', 'CREATED', 'CREDITED'))
);
create index if not exists idx_wallet_topup_intents_company
    on wallet_topup_intents(company_id, created_at desc);
create unique index if not exists uk_wallet_topup_provider_payment
    on wallet_topup_intents(provider_payment_id) where provider_payment_id is not null;
