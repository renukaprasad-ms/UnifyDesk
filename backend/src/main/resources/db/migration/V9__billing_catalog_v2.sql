-- Billing catalogue v2: versioned plan prices, typed entitlements, pricing overrides,
-- subscription lifecycle and idempotent usage snapshots.

alter table plans add column if not exists description varchar(500);

update plans set status = 'PUBLISHED' where status = 'ACTIVE';
update plans set status = 'RETIRED' where status = 'ARCHIVED';

create table if not exists entitlement_definitions (
  id bigint primary key,
  entitlement_key varchar(120) not null,
  name varchar(160) not null,
  description varchar(500),
  value_type varchar(32) not null,
  category varchar(32) not null,
  default_value varchar(512) not null,
  active boolean not null default true,
  created_at timestamp(6) with time zone not null,
  updated_at timestamp(6) with time zone not null,
  constraint uk_entitlement_definitions_key unique (entitlement_key)
);

insert into entitlement_definitions
  (id, entitlement_key, name, description, value_type, category, default_value, active, created_at, updated_at)
values
  (910000000000000001, 'MAX_WORKSPACE_USERS', 'Workspace users', 'Maximum active users in the workspace', 'INTEGER', 'WORKSPACE', '1', true, now(), now()),
  (910000000000000002, 'EMAIL_ACCOUNTS', 'Email connections', 'Maximum connected email accounts', 'INTEGER', 'CHANNEL', '0', true, now(), now()),
  (910000000000000003, 'WHATSAPP_ACCOUNTS', 'WhatsApp connections', 'Maximum connected WhatsApp business accounts', 'INTEGER', 'CHANNEL', '0', true, now(), now()),
  (910000000000000004, 'WHATSAPP_NUMBERS', 'WhatsApp numbers', 'Maximum WhatsApp sender phone numbers', 'INTEGER', 'CHANNEL', '0', true, now(), now()),
  (910000000000000005, 'SMS_ACCOUNTS', 'SMS connections', 'Maximum SMS provider connections', 'INTEGER', 'CHANNEL', '0', true, now(), now()),
  (910000000000000006, 'SMS_SENDER_IDS', 'SMS sender IDs', 'Maximum SMS sender IDs/headers', 'INTEGER', 'CHANNEL', '0', true, now(), now()),
  (910000000000000007, 'BROADCAST_BATCH_LIMIT', 'Recipients per broadcast', 'Maximum recipients in one broadcast', 'INTEGER', 'BROADCAST', '0', true, now(), now()),
  (910000000000000008, 'BROADCASTS_PER_MONTH', 'Broadcasts per month', 'Maximum broadcasts per billing month', 'INTEGER', 'BROADCAST', '0', true, now(), now()),
  (910000000000000009, 'BROADCAST_RECIPIENTS_PER_MONTH', 'Broadcast recipients per month', 'Maximum total broadcast recipients per billing month', 'INTEGER', 'BROADCAST', '0', true, now(), now()),
  (910000000000000010, 'EMAIL_TEMPLATE_LIMIT', 'Email templates', 'Maximum saved email templates', 'INTEGER', 'TEMPLATE', '0', true, now(), now()),
  (910000000000000011, 'SOCIAL_TEMPLATE_LIMIT', 'WhatsApp templates', 'Maximum saved WhatsApp/social templates', 'INTEGER', 'TEMPLATE', '0', true, now(), now()),
  (910000000000000012, 'AUTO_REPLY_ENABLED', 'Auto reply', 'Enable automated replies', 'BOOLEAN', 'FEATURE', 'false', true, now(), now()),
  (910000000000000013, 'USER_SIGNATURE_ENABLED', 'User signatures', 'Enable per-user message signatures', 'BOOLEAN', 'FEATURE', 'false', true, now(), now()),
  (910000000000000014, 'SCHEDULED_BROADCASTS_ENABLED', 'Scheduled broadcasts', 'Enable scheduled broadcasts', 'BOOLEAN', 'BROADCAST', 'false', true, now(), now()),
  (910000000000000015, 'API_ACCESS_ENABLED', 'API access', 'Enable customer API access', 'BOOLEAN', 'FEATURE', 'false', true, now(), now())
on conflict (entitlement_key) do update set
  name = excluded.name,
  description = excluded.description,
  value_type = excluded.value_type,
  category = excluded.category,
  default_value = excluded.default_value,
  active = excluded.active,
  updated_at = now();

create table if not exists plan_prices (
  id bigint primary key,
  plan_id bigint not null,
  billing_cycle varchar(32) not null,
  amount numeric(19, 6) not null,
  currency varchar(3) not null,
  effective_from timestamp(6) with time zone not null,
  effective_to timestamp(6) with time zone,
  status varchar(32) not null,
  created_at timestamp(6) with time zone not null,
  updated_at timestamp(6) with time zone not null,
  constraint fk_plan_prices_plan foreign key (plan_id) references plans(id),
  constraint ck_plan_prices_amount_non_negative check (amount >= 0),
  constraint ck_plan_prices_period check (effective_to is null or effective_to > effective_from)
);

create index if not exists idx_plan_prices_plan_status on plan_prices (plan_id, status);
create index if not exists idx_plan_prices_effective on plan_prices (effective_from, effective_to);

-- Every legacy plan gets a first price so existing Free/current plans continue to work.
insert into plan_prices
  (id, plan_id, billing_cycle, amount, currency, effective_from, effective_to, status, created_at, updated_at)
select
  p.id + 700000000000000000,
  p.id,
  p.billing_cycle,
  p.base_price,
  p.currency,
  p.created_at,
  null,
  'ACTIVE',
  now(),
  now()
from plans p
where not exists (select 1 from plan_prices pp where pp.plan_id = p.id);

alter table company_subscriptions add column if not exists plan_price_id bigint;
alter table company_subscriptions add column if not exists current_period_start timestamp(6) with time zone;
alter table company_subscriptions add column if not exists current_period_end timestamp(6) with time zone;
alter table company_subscriptions add column if not exists trial_ends_at timestamp(6) with time zone;
alter table company_subscriptions add column if not exists cancel_at_period_end boolean not null default false;
alter table company_subscriptions add column if not exists cancelled_at timestamp(6) with time zone;

update company_subscriptions
set current_period_start = coalesce(current_period_start, started_at)
where current_period_start is null;

-- Attach the first matching legacy price where possible.
update company_subscriptions cs
set plan_price_id = pp.id
from plan_prices pp
where cs.plan_price_id is null
  and pp.plan_id = cs.plan_id
  and pp.status = 'ACTIVE'
  and pp.billing_cycle = (select p.billing_cycle from plans p where p.id = cs.plan_id);

-- Keep only the newest effective subscription before enforcing uniqueness.
with ranked as (
  select id,
         row_number() over (partition by company_id order by started_at desc, created_at desc, id desc) as rn
  from company_subscriptions
  where status in ('ACTIVE', 'TRIALING', 'PAST_DUE')
)
update company_subscriptions cs
set status = 'CANCELLED',
    cancelled_at = coalesce(cancelled_at, now()),
    ends_at = coalesce(ends_at, now()),
    updated_at = now()
from ranked r
where cs.id = r.id and r.rn > 1;

create unique index if not exists uk_company_subscriptions_current
  on company_subscriptions (company_id)
  where status in ('ACTIVE', 'TRIALING', 'PAST_DUE');

create index if not exists idx_company_subscriptions_plan_price on company_subscriptions (plan_price_id);

alter table company_subscriptions
  drop constraint if exists fk_company_subscriptions_plan_price;
alter table company_subscriptions
  add constraint fk_company_subscriptions_plan_price
  foreign key (plan_price_id) references plan_prices(id);

-- Normalize legacy pricing dimensions to the canonical channel/provider model.
update rate_cards
set provider = case channel
  when 'EMAIL' then coalesce(nullif(provider, ''), 'SMTP')
  when 'SMS' then coalesce(nullif(provider, ''), 'MSG91')
  when 'WHATSAPP' then coalesce(nullif(provider, ''), 'META_WHATSAPP')
  else provider
end;

update rate_cards
set message_type = case
  when channel = 'SMS' and message_type = 'PROMOTIONAL' then 'SMS_PROMOTIONAL'
  when channel = 'SMS' and message_type = 'TRANSACTIONAL' then 'SMS_TRANSACTIONAL'
  when channel = 'SMS' and message_type = 'OTP' then 'SMS_OTP'
  when channel = 'WHATSAPP' and message_type = 'PROMOTIONAL' then 'WHATSAPP_MARKETING'
  when channel = 'WHATSAPP' and message_type = 'TRANSACTIONAL' then 'WHATSAPP_UTILITY'
  when channel = 'WHATSAPP' and message_type = 'OTP' then 'WHATSAPP_AUTHENTICATION'
  when channel = 'WHATSAPP' and message_type = 'CONVERSATIONAL' then 'WHATSAPP_SERVICE'
  when channel = 'EMAIL' then 'EMAIL_OUTBOUND'
  else message_type
end;

update rate_cards set direction = 'OUTBOUND' where direction in ('ONE_WAY', 'TWO_WAY');

create table if not exists company_rate_overrides (
  id bigint primary key,
  company_id bigint not null,
  channel varchar(32) not null,
  provider varchar(80) not null,
  billing_category varchar(64) not null,
  direction varchar(32) not null,
  country varchar(2),
  carrier varchar(80),
  unit varchar(32) not null,
  price numeric(19, 6) not null,
  currency varchar(3) not null,
  effective_from timestamp(6) with time zone not null,
  effective_to timestamp(6) with time zone,
  status varchar(32) not null,
  created_by_user_id bigint,
  reason varchar(500),
  created_at timestamp(6) with time zone not null,
  updated_at timestamp(6) with time zone not null,
  constraint fk_company_rate_overrides_company foreign key (company_id) references companies(id),
  constraint ck_company_rate_overrides_price_non_negative check (price >= 0),
  constraint ck_company_rate_overrides_period check (effective_to is null or effective_to > effective_from)
);

create index if not exists idx_company_rate_overrides_company_status
  on company_rate_overrides (company_id, status);
create index if not exists idx_company_rate_overrides_effective
  on company_rate_overrides (effective_from, effective_to);

-- Upgrade historical usage rows without deleting billing history.
update usage_events
set provider = case channel
  when 'EMAIL' then coalesce(nullif(provider, ''), 'SMTP')
  when 'SMS' then coalesce(nullif(provider, ''), 'MSG91')
  when 'WHATSAPP' then coalesce(nullif(provider, ''), 'META_WHATSAPP')
  else provider
end;

update usage_events
set message_type = case
  when channel = 'SMS' and message_type = 'PROMOTIONAL' then 'SMS_PROMOTIONAL'
  when channel = 'SMS' and message_type = 'TRANSACTIONAL' then 'SMS_TRANSACTIONAL'
  when channel = 'SMS' and message_type = 'OTP' then 'SMS_OTP'
  when channel = 'WHATSAPP' and message_type = 'PROMOTIONAL' then 'WHATSAPP_MARKETING'
  when channel = 'WHATSAPP' and message_type = 'TRANSACTIONAL' then 'WHATSAPP_UTILITY'
  when channel = 'WHATSAPP' and message_type = 'OTP' then 'WHATSAPP_AUTHENTICATION'
  when channel = 'WHATSAPP' and message_type = 'CONVERSATIONAL' then 'WHATSAPP_SERVICE'
  when channel = 'EMAIL' then 'EMAIL_OUTBOUND'
  else message_type
end;

update usage_events set direction = 'OUTBOUND' where direction in ('ONE_WAY', 'TWO_WAY');

alter table usage_events add column if not exists pricing_rule_id bigint;
alter table usage_events add column if not exists pricing_source varchar(32);
alter table usage_events add column if not exists source_type varchar(64);
alter table usage_events add column if not exists source_id varchar(160);
alter table usage_events add column if not exists idempotency_key varchar(255);

update usage_events
set source_type = coalesce(source_type, 'LEGACY'),
    source_id = coalesce(source_id, id::text),
    idempotency_key = coalesce(idempotency_key, 'legacy:' || id::text),
    pricing_rule_id = coalesce(pricing_rule_id, rate_card_id),
    pricing_source = coalesce(pricing_source, case when rate_card_id is null then null else 'PLAN_RATE' end);

alter table usage_events alter column source_type set not null;
alter table usage_events alter column source_id set not null;
alter table usage_events alter column idempotency_key set not null;

create unique index if not exists uk_usage_events_idempotency on usage_events (idempotency_key);
create index if not exists idx_usage_events_source on usage_events (source_type, source_id);
