-- Persist provider-order binding before any merchant-initiated recurring debit.
-- The provider order may be reconciled after timeout; a command is never resubmitted blindly.
create table if not exists razorpay_recurring_charge_intents (
  command_id varchar(128) primary key,
  provider_order_id varchar(128) not null unique,
  provider_token_id varchar(128) not null,
  amount numeric(19,4) not null check (amount > 0),
  currency varchar(3) not null check (currency = 'INR'),
  provider_payment_id varchar(128) unique,
  created_at timestamp(6) with time zone not null default now(),
  updated_at timestamp(6) with time zone not null default now()
);
