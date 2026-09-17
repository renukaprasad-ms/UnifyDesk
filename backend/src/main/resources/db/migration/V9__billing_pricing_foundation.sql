-- Plan lifecycle: existing ACTIVE plans are already customer-visible.
update plans set status = 'PUBLISHED' where status = 'ACTIVE';
update plans set status = 'RETIRED' where status = 'ARCHIVED';

create table if not exists entitlement_definitions (
  entitlement_key varchar(120) primary key,
  label varchar(160) not null,
  description varchar(500),
  value_type varchar(32) not null,
  category varchar(32) not null,
  default_value varchar(512),
  allow_unlimited boolean not null default false,
  active boolean not null default true
);

insert into entitlement_definitions
  (entitlement_key, label, description, value_type, category, default_value, allow_unlimited, active)
values
  ('MAX_WORKSPACE_USERS', 'Workspace users', 'Maximum active users in a workspace.', 'INTEGER', 'WORKSPACE', '1', true, true),
  ('EMAIL_ACCOUNTS', 'Email connections', 'Maximum connected email accounts.', 'INTEGER', 'CHANNEL', '0', true, true),
  ('WHATSAPP_ACCOUNTS', 'WhatsApp connections', 'Maximum connected WhatsApp business accounts.', 'INTEGER', 'CHANNEL', '0', true, true),
  ('WHATSAPP_NUMBERS', 'WhatsApp numbers', 'Maximum WhatsApp sender phone numbers.', 'INTEGER', 'CHANNEL', '0', true, true),
  ('SMS_ACCOUNTS', 'SMS connections', 'Maximum connected SMS provider accounts.', 'INTEGER', 'CHANNEL', '0', true, true),
  ('SMS_SENDER_IDS', 'SMS sender IDs', 'Maximum configured SMS sender IDs or headers.', 'INTEGER', 'CHANNEL', '0', true, true),
  ('BROADCAST_BATCH_LIMIT', 'Recipients per broadcast', 'Maximum recipients in one broadcast.', 'INTEGER', 'BROADCAST', '0', true, true),
  ('BROADCASTS_PER_MONTH', 'Broadcasts per month', 'Maximum broadcasts started in one billing month.', 'INTEGER', 'BROADCAST', '0', true, true),
  ('BROADCAST_RECIPIENTS_PER_MONTH', 'Broadcast recipients per month', 'Maximum aggregate broadcast recipients in one billing month.', 'INTEGER', 'BROADCAST', '0', true, true),
  ('EMAIL_TEMPLATE_LIMIT', 'Email templates', 'Maximum saved email templates.', 'INTEGER', 'TEMPLATE', '0', true, true),
  ('SOCIAL_TEMPLATE_LIMIT', 'Social templates', 'Maximum saved social or WhatsApp templates.', 'INTEGER', 'TEMPLATE', '0', true, true),
  ('SCHEDULED_BROADCASTS', 'Scheduled broadcasts', 'Allow scheduling broadcasts for future delivery.', 'BOOLEAN', 'FEATURE', 'false', false, true),
  ('API_ACCESS', 'API access', 'Allow customer API access.', 'BOOLEAN', 'FEATURE', 'false', false, true),
  ('AUTO_REPLY_ENABLED', 'Auto reply', 'Allow automated replies.', 'BOOLEAN', 'FEATURE', 'false', false, true),
  ('USER_SIGNATURE_ENABLED', 'User signatures', 'Allow per-user message signatures.', 'BOOLEAN', 'FEATURE', 'false', false, true)
on conflict (entitlement_key) do update set
  label = excluded.label,
  description = excluded.description,
  value_type = excluded.value_type,
  category = excluded.category,
  default_value = excluded.default_value,
  allow_unlimited = excluded.allow_unlimited,
  active = excluded.active;

-- Fail-closed channel limits for the seeded free plan. Admins can change these later.
insert into plan_entitlements
  (id, plan_id, entitlement_key, entitlement_value, created_at, updated_at)
select values_to_insert.id,
       100000000000000001,
       values_to_insert.entitlement_key,
       values_to_insert.entitlement_value,
       now(),
       now()
from (values
  (100000000000000021::bigint, 'EMAIL_ACCOUNTS'::varchar, '0'::varchar),
  (100000000000000022::bigint, 'WHATSAPP_ACCOUNTS'::varchar, '0'::varchar),
  (100000000000000023::bigint, 'WHATSAPP_NUMBERS'::varchar, '0'::varchar),
  (100000000000000024::bigint, 'SMS_ACCOUNTS'::varchar, '0'::varchar),
  (100000000000000025::bigint, 'SMS_SENDER_IDS'::varchar, '0'::varchar),
  (100000000000000026::bigint, 'BROADCASTS_PER_MONTH'::varchar, '0'::varchar),
  (100000000000000027::bigint, 'BROADCAST_RECIPIENTS_PER_MONTH'::varchar, '0'::varchar),
  (100000000000000028::bigint, 'SCHEDULED_BROADCASTS'::varchar, 'false'::varchar),
  (100000000000000029::bigint, 'API_ACCESS'::varchar, 'false'::varchar)
) as values_to_insert(id, entitlement_key, entitlement_value)
where exists (select 1 from plans where id = 100000000000000001)
on conflict (plan_id, entitlement_key) do nothing;

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
  constraint fk_plan_prices_plan foreign key (plan_id) references plans(id)
);

insert into plan_prices
  (id, plan_id, billing_cycle, amount, currency, effective_from, effective_to, status, created_at, updated_at)
select p.id,
       p.id,
       p.billing_cycle,
       p.base_price,
       p.currency,
       p.created_at,
       null,
       'ACTIVE',
       p.created_at,
       p.updated_at
from plans p
where not exists (select 1 from plan_prices pp where pp.plan_id = p.id);

create index if not exists idx_plan_prices_plan_cycle_status
  on plan_prices (plan_id, billing_cycle, status);
create index if not exists idx_plan_prices_effective
  on plan_prices (effective_from, effective_to);

alter table company_subscriptions add column if not exists plan_price_id bigint;
alter table company_subscriptions add column if not exists current_period_start timestamp(6) with time zone;
alter table company_subscriptions add column if not exists current_period_end timestamp(6) with time zone;
alter table company_subscriptions add column if not exists trial_ends_at timestamp(6) with time zone;
alter table company_subscriptions add column if not exists cancel_at_period_end boolean not null default false;
alter table company_subscriptions add column if not exists cancelled_at timestamp(6) with time zone;

update company_subscriptions cs
set plan_price_id = pp.id
from plan_prices pp
where cs.plan_id = pp.plan_id
  and cs.plan_price_id is null
  and pp.status = 'ACTIVE'
  and pp.billing_cycle = (select p.billing_cycle from plans p where p.id = cs.plan_id);

update company_subscriptions
set current_period_start = started_at
where current_period_start is null;

-- Keep only the newest current subscription before enforcing the invariant.
with ranked as (
  select id,
         row_number() over (
           partition by company_id
           order by started_at desc, created_at desc, id desc
         ) as rn
  from company_subscriptions
  where status in ('ACTIVE', 'TRIALING', 'PAST_DUE')
)
update company_subscriptions cs
set status = 'CANCELLED',
    cancelled_at = coalesce(cs.cancelled_at, now()),
    ends_at = coalesce(cs.ends_at, now()),
    updated_at = now()
from ranked r
where cs.id = r.id and r.rn > 1;

create unique index if not exists uk_company_subscriptions_current
  on company_subscriptions (company_id)
  where status in ('ACTIVE', 'TRIALING', 'PAST_DUE');

-- New provider-aware billing dimensions. Legacy message_type stays for rollback/history compatibility.
alter table rate_cards add column if not exists billing_category varchar(48);
update rate_cards
set billing_category = case
  when channel = 'EMAIL' and direction in ('ONE_WAY', 'TWO_WAY', 'OUTBOUND') then 'EMAIL_OUTBOUND'
  when channel = 'SMS' and message_type = 'PROMOTIONAL' then 'SMS_PROMOTIONAL'
  when channel = 'SMS' and message_type = 'OTP' then 'SMS_OTP'
  when channel = 'SMS' then 'SMS_TRANSACTIONAL'
  when channel = 'WHATSAPP' and message_type = 'PROMOTIONAL' then 'WHATSAPP_MARKETING'
  when channel = 'WHATSAPP' and message_type = 'OTP' then 'WHATSAPP_AUTHENTICATION'
  when channel = 'WHATSAPP' and message_type = 'TRANSACTIONAL' then 'WHATSAPP_UTILITY'
  when channel = 'WHATSAPP' then 'WHATSAPP_SERVICE'
  else 'EMAIL_OUTBOUND'
end
where billing_category is null;
update rate_cards set direction = 'OUTBOUND' where direction in ('ONE_WAY', 'TWO_WAY');
alter table rate_cards alter column billing_category set not null;
create index if not exists idx_rate_cards_lookup
  on rate_cards (plan_id, provider, billing_category, direction, status);

create table if not exists company_rate_overrides (
  id bigint primary key,
  company_id bigint not null,
  channel varchar(32) not null,
  provider varchar(80) not null,
  billing_category varchar(48) not null,
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
  constraint fk_company_rate_overrides_company foreign key (company_id) references companies(id)
);
create index if not exists idx_company_rate_overrides_lookup
  on company_rate_overrides (company_id, provider, billing_category, status);
create index if not exists idx_company_rate_overrides_effective
  on company_rate_overrides (effective_from, effective_to);

alter table usage_events add column if not exists billing_category varchar(48);
alter table usage_events add column if not exists pricing_source varchar(32);
alter table usage_events add column if not exists pricing_rule_id bigint;
alter table usage_events add column if not exists source_type varchar(48);
alter table usage_events add column if not exists source_id varchar(120);
alter table usage_events add column if not exists idempotency_key varchar(200);

update usage_events
set billing_category = case
  when channel = 'EMAIL' and direction in ('ONE_WAY', 'TWO_WAY', 'OUTBOUND') then 'EMAIL_OUTBOUND'
  when channel = 'SMS' and message_type = 'PROMOTIONAL' then 'SMS_PROMOTIONAL'
  when channel = 'SMS' and message_type = 'OTP' then 'SMS_OTP'
  when channel = 'SMS' then 'SMS_TRANSACTIONAL'
  when channel = 'WHATSAPP' and message_type = 'PROMOTIONAL' then 'WHATSAPP_MARKETING'
  when channel = 'WHATSAPP' and message_type = 'OTP' then 'WHATSAPP_AUTHENTICATION'
  when channel = 'WHATSAPP' and message_type = 'TRANSACTIONAL' then 'WHATSAPP_UTILITY'
  when channel = 'WHATSAPP' then 'WHATSAPP_SERVICE'
  else 'EMAIL_OUTBOUND'
end
where billing_category is null;
update usage_events set direction = 'OUTBOUND' where direction in ('ONE_WAY', 'TWO_WAY');
update usage_events
set pricing_source = case when rate_card_id is not null then 'PLAN_RATE' else pricing_source end,
    pricing_rule_id = coalesce(pricing_rule_id, rate_card_id),
    idempotency_key = coalesce(idempotency_key, 'legacy:' || id::text)
where idempotency_key is null or pricing_rule_id is null;
alter table usage_events alter column billing_category set not null;
alter table usage_events alter column idempotency_key set not null;
create unique index if not exists uk_usage_events_idempotency_key on usage_events (idempotency_key);
create index if not exists idx_usage_events_source on usage_events (source_type, source_id);
