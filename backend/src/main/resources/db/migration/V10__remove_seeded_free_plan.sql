-- V2 seeded a bootstrap Free plan before plans became admin-managed.
-- Keep V2 immutable for Flyway checksum compatibility and remove only that known seed here.
-- Any plan created later by an administrator (including one named/code FREE with another id)
-- is intentionally untouched.

-- Preserve historical usage rows if any were recorded against the bootstrap plan.
update usage_events
set plan_id = null
where plan_id = 100000000000000001;

-- Remove subscriptions that were automatically attached to the bootstrap plan.
-- Companies are allowed to exist without a subscription until an administrator assigns one.
delete from company_subscriptions
where plan_id = 100000000000000001;

-- Remove all commercial children before deleting the bootstrap plan.
delete from rate_cards
where plan_id = 100000000000000001;

delete from plan_entitlements
where plan_id = 100000000000000001;

delete from plan_prices
where plan_id = 100000000000000001;

delete from plans
where id = 100000000000000001
  and code = 'FREE';
