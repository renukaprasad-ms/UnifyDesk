-- Billing catalog and usage history
-- Added tables represented by JPA entities:
-- - plans
-- - plan_entitlements
-- - company_subscriptions
-- - rate_cards
-- - usage_events
--
-- Initial FREE plan seed. Apply after the billing tables exist.

insert into plans (
  id,
  code,
  name,
  status,
  billing_cycle,
  base_price,
  currency,
  version,
  created_at,
  updated_at
) values (
  100000000000000001,
  'FREE',
  'Free',
  'ACTIVE',
  'FREE',
  0.000000,
  'USD',
  1,
  now(),
  now()
) on conflict (code, version) do update set
  name = excluded.name,
  status = excluded.status,
  billing_cycle = excluded.billing_cycle,
  base_price = excluded.base_price,
  currency = excluded.currency,
  updated_at = now();

insert into plan_entitlements (
  id,
  plan_id,
  entitlement_key,
  entitlement_value,
  created_at,
  updated_at
) values
  (100000000000000011, 100000000000000001, 'MAX_WORKSPACE_USERS', '1', now(), now()),
  (100000000000000012, 100000000000000001, 'BROADCAST_BATCH_LIMIT', '500', now(), now()),
  (100000000000000013, 100000000000000001, 'EMAIL_TEMPLATE_LIMIT', '1', now(), now()),
  (100000000000000014, 100000000000000001, 'SOCIAL_TEMPLATE_LIMIT', '1', now(), now()),
  (100000000000000015, 100000000000000001, 'AUTO_REPLY_ENABLED', 'false', now(), now()),
  (100000000000000016, 100000000000000001, 'USER_SIGNATURE_ENABLED', 'false', now(), now())
on conflict (plan_id, entitlement_key) do update set
  entitlement_value = excluded.entitlement_value,
  updated_at = now();
