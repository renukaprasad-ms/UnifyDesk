-- Seed the canonical FREE v1 plan used as the default subscription.
-- This migration runs exactly once. It backfills only companies that are ACTIVE
-- at migration time and do not already have an effective subscription.
-- Future companies receive the default plan from application onboarding logic.

insert into plans (
  id,
  code,
  name,
  description,
  status,
  billing_cycle,
  base_price,
  currency,
  version,
  created_at,
  updated_at
) values (
  920000000000000001,
  'FREE',
  'Free',
  'Free workspace plan for evaluating core messaging features with conservative usage limits.',
  'PUBLISHED',
  'FREE',
  0.000000,
  'INR',
  1,
  now(),
  now()
)
on conflict (code, version) do update set
  name = excluded.name,
  description = excluded.description,
  status = 'PUBLISHED',
  billing_cycle = 'FREE',
  base_price = 0.000000,
  currency = 'INR',
  updated_at = now();

-- Ensure FREE v1 has one active zero-cost price. Reuse an existing FREE price if present.
update plan_prices pp
set amount = 0.000000,
    currency = 'INR',
    effective_to = null,
    status = 'ACTIVE',
    updated_at = now()
from plans p
where pp.plan_id = p.id
  and p.code = 'FREE'
  and p.version = 1
  and pp.billing_cycle = 'FREE'
  and pp.status = 'ACTIVE';

insert into plan_prices (
  id,
  plan_id,
  billing_cycle,
  amount,
  currency,
  effective_from,
  effective_to,
  status,
  created_at,
  updated_at
)
select
  920000000000000002,
  p.id,
  'FREE',
  0.000000,
  'INR',
  now(),
  null,
  'ACTIVE',
  now(),
  now()
from plans p
where p.code = 'FREE'
  and p.version = 1
  and not exists (
    select 1
    from plan_prices pp
    where pp.plan_id = p.id
      and pp.billing_cycle = 'FREE'
      and pp.status = 'ACTIVE'
  );

-- Free-plan limits. Messaging provider usage remains billable through rate cards;
-- this plan only grants product capacity/features.
insert into plan_entitlements (
  id,
  plan_id,
  entitlement_key,
  entitlement_value,
  created_at,
  updated_at
)
select seed.id, p.id, seed.entitlement_key, seed.entitlement_value, now(), now()
from plans p
cross join (
  values
    (920000000000000011::bigint, 'MAX_WORKSPACE_USERS', '2'),
    (920000000000000012::bigint, 'EMAIL_ACCOUNTS', '1'),
    (920000000000000013::bigint, 'WHATSAPP_ACCOUNTS', '1'),
    (920000000000000014::bigint, 'WHATSAPP_NUMBERS', '1'),
    (920000000000000015::bigint, 'SMS_ACCOUNTS', '1'),
    (920000000000000016::bigint, 'SMS_SENDER_IDS', '1'),
    (920000000000000017::bigint, 'BROADCAST_BATCH_LIMIT', '100'),
    (920000000000000018::bigint, 'BROADCASTS_PER_MONTH', '10'),
    (920000000000000019::bigint, 'BROADCAST_RECIPIENTS_PER_MONTH', '500'),
    (920000000000000020::bigint, 'EMAIL_TEMPLATE_LIMIT', '5'),
    (920000000000000021::bigint, 'SOCIAL_TEMPLATE_LIMIT', '5'),
    (920000000000000022::bigint, 'SCHEDULED_BROADCASTS_ENABLED', 'false'),
    (920000000000000023::bigint, 'AUTO_REPLY_ENABLED', 'false'),
    (920000000000000024::bigint, 'USER_SIGNATURE_ENABLED', 'true'),
    (920000000000000025::bigint, 'API_ACCESS_ENABLED', 'false')
) as seed(id, entitlement_key, entitlement_value)
where p.code = 'FREE'
  and p.version = 1
on conflict (plan_id, entitlement_key) do update set
  entitlement_value = excluded.entitlement_value,
  updated_at = now();

-- One-time backfill: give FREE v1 only to ACTIVE companies that do not already
-- have an ACTIVE/TRIALING/PAST_DUE subscription. Existing paid/trial subscriptions
-- are intentionally left untouched.
with free_plan as (
  select p.id as plan_id,
         (
           select pp.id
           from plan_prices pp
           where pp.plan_id = p.id
             and pp.billing_cycle = 'FREE'
             and pp.status = 'ACTIVE'
           order by pp.effective_from desc, pp.id desc
           limit 1
         ) as plan_price_id
  from plans p
  where p.code = 'FREE'
    and p.version = 1
    and p.status = 'PUBLISHED'
  limit 1
), eligible_companies as (
  select c.id as company_id,
         row_number() over (order by c.id) as row_num
  from companies c
  where c.status = 'ACTIVE'
    and not exists (
      select 1
      from company_subscriptions cs
      where cs.company_id = c.id
        and cs.status in ('ACTIVE', 'TRIALING', 'PAST_DUE')
    )
)
insert into company_subscriptions (
  id,
  company_id,
  plan_id,
  plan_price_id,
  status,
  started_at,
  ends_at,
  current_period_start,
  current_period_end,
  trial_ends_at,
  cancel_at_period_end,
  cancelled_at,
  created_at,
  updated_at
)
select
  930000000000000000 + ec.row_num,
  ec.company_id,
  fp.plan_id,
  fp.plan_price_id,
  'ACTIVE',
  now(),
  null,
  now(),
  null,
  null,
  false,
  null,
  now(),
  now()
from eligible_companies ec
cross join free_plan fp;
