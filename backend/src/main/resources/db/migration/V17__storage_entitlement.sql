insert into entitlement_definitions (
  id,
  entitlement_key,
  name,
  description,
  value_type,
  category,
  default_value,
  active,
  created_at,
  updated_at
) values (
  910000000000000016,
  'STORAGE_GB',
  'Workspace storage',
  'Maximum private media storage available to the workspace in GiB',
  'DECIMAL',
  'STORAGE',
  '2',
  true,
  now(),
  now()
)
on conflict (entitlement_key) do update set
  name = excluded.name,
  description = excluded.description,
  value_type = excluded.value_type,
  category = excluded.category,
  default_value = excluded.default_value,
  active = excluded.active,
  updated_at = now();

insert into plan_entitlements (
  id,
  plan_id,
  entitlement_key,
  entitlement_value,
  created_at,
  updated_at
)
select
  920000000000000025,
  p.id,
  'STORAGE_GB',
  '2',
  now(),
  now()
from plans p
where p.code = 'FREE'
  and p.version = 1
on conflict (plan_id, entitlement_key) do update set
  entitlement_value = excluded.entitlement_value,
  updated_at = now();

create index if not exists idx_media_company_status on media(company_id, status);
