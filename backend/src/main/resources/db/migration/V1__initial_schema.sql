create table if not exists media (
  id bigint primary key,
  owner_user_id bigint not null,
  company_id bigint,
  storage_provider varchar(32) not null,
  bucket varchar(120) not null,
  object_key varchar(1024) not null,
  original_file_name varchar(255) not null,
  content_type varchar(120) not null,
  size_bytes bigint not null,
  media_type varchar(32) not null,
  purpose varchar(48) not null,
  status varchar(32) not null,
  checksum varchar(128),
  created_at timestamp(6) with time zone not null,
  completed_at timestamp(6) with time zone,
  deleted_at timestamp(6) with time zone,
  constraint uk_media_storage_object unique (storage_provider, bucket, object_key)
);

create table if not exists users (
  id bigint primary key,
  email varchar(320) not null,
  name varchar(120) not null,
  is_verified boolean not null,
  profile_picture_media_id bigint,
  username varchar(50) not null,
  password varchar(255) not null,
  two_factor_enabled boolean not null,
  constraint uk_users_email unique (email),
  constraint uk_users_username unique (username)
);

create table if not exists companies (
  id bigint primary key,
  name varchar(160) not null,
  slug varchar(180) not null,
  business_email varchar(254),
  country varchar(80),
  timezone varchar(80),
  status varchar(32) not null,
  created_by_user_id bigint not null,
  created_at timestamp(6) with time zone not null,
  updated_at timestamp(6) with time zone not null,
  deleted_at timestamp(6) with time zone,
  constraint uk_companies_slug unique (slug)
);

create table if not exists company_members (
  id bigint primary key,
  company_id bigint not null,
  user_id bigint not null,
  role varchar(32) not null,
  status varchar(32) not null,
  created_at timestamp(6) with time zone not null,
  updated_at timestamp(6) with time zone not null,
  constraint uk_company_members_company_user unique (company_id, user_id)
);

create table if not exists company_access_profiles (
  id bigint primary key,
  company_id bigint not null,
  name varchar(120) not null,
  description varchar(500),
  created_by_user_id bigint not null,
  created_at timestamp(6) with time zone not null,
  constraint uk_company_access_profiles_name unique (company_id, name)
);

create table if not exists company_access_profile_permissions (
  id bigint primary key,
  profile_id bigint not null,
  permission varchar(64) not null,
  constraint uk_company_access_profile_permissions unique (profile_id, permission)
);

create table if not exists company_member_access_profile_grants (
  id bigint primary key,
  company_id bigint not null,
  user_id bigint not null,
  profile_id bigint not null,
  expires_at timestamp(6) with time zone,
  created_by_user_id bigint not null,
  created_at timestamp(6) with time zone not null,
  revoked_at timestamp(6) with time zone
);

create table if not exists company_member_permission_overrides (
  id bigint primary key,
  company_id bigint not null,
  user_id bigint not null,
  permission varchar(64) not null,
  effect varchar(16) not null,
  reason varchar(255),
  expires_at timestamp(6) with time zone,
  created_by_user_id bigint not null,
  created_at timestamp(6) with time zone not null,
  revoked_at timestamp(6) with time zone
);

create table if not exists company_role_permissions (
  id bigint primary key,
  company_id bigint,
  role varchar(32) not null,
  permission varchar(64) not null,
  effect varchar(16) not null,
  constraint uk_company_role_permissions_scope unique (company_id, role, permission)
);

create table if not exists user_sessions (
  id bigint primary key,
  user_id bigint not null,
  platform varchar(20) not null,
  refresh_token_id varchar(255) not null,
  ip_address varchar(45),
  user_agent varchar(512),
  device_name varchar(120),
  created_at timestamp(6) with time zone not null,
  last_seen_at timestamp(6) with time zone not null,
  expires_at timestamp(6) with time zone not null,
  revoked_at timestamp(6) with time zone,
  constraint uk_user_sessions_user_platform unique (user_id, platform),
  constraint uk_user_sessions_refresh_token_id unique (refresh_token_id)
);

create table if not exists platform_admins (
  id bigint primary key,
  user_id bigint not null,
  role varchar(32) not null,
  status varchar(32) not null,
  created_at timestamp(6) with time zone not null,
  constraint uk_platform_admins_user unique (user_id)
);

create table if not exists admin_company_assignments (
  id bigint primary key,
  admin_user_id bigint not null,
  company_id bigint not null,
  assigned_by_user_id bigint not null,
  created_at timestamp(6) with time zone not null,
  constraint uk_admin_company_assignments_admin_company unique (admin_user_id, company_id)
);

create table if not exists plans (
  id bigint primary key,
  code varchar(64) not null,
  name varchar(120) not null,
  status varchar(32) not null,
  billing_cycle varchar(32) not null,
  base_price numeric(19, 6) not null,
  currency varchar(3) not null,
  version integer not null,
  created_at timestamp(6) with time zone not null,
  updated_at timestamp(6) with time zone not null,
  constraint uk_plans_code_version unique (code, version)
);

create table if not exists plan_entitlements (
  id bigint primary key,
  plan_id bigint not null,
  entitlement_key varchar(120) not null,
  entitlement_value varchar(512) not null,
  created_at timestamp(6) with time zone not null,
  updated_at timestamp(6) with time zone not null,
  constraint uk_plan_entitlements_plan_key unique (plan_id, entitlement_key)
);

create table if not exists company_subscriptions (
  id bigint primary key,
  company_id bigint not null,
  plan_id bigint not null,
  status varchar(32) not null,
  started_at timestamp(6) with time zone not null,
  ends_at timestamp(6) with time zone,
  created_at timestamp(6) with time zone not null,
  updated_at timestamp(6) with time zone not null
);

create table if not exists rate_cards (
  id bigint primary key,
  plan_id bigint not null,
  channel varchar(32) not null,
  provider varchar(80),
  country varchar(2),
  carrier varchar(80),
  message_type varchar(32) not null,
  direction varchar(32) not null,
  unit varchar(32) not null,
  price numeric(19, 6) not null,
  currency varchar(3) not null,
  effective_from timestamp(6) with time zone not null,
  effective_to timestamp(6) with time zone,
  status varchar(32) not null,
  created_at timestamp(6) with time zone not null,
  updated_at timestamp(6) with time zone not null
);

create table if not exists usage_events (
  id bigint primary key,
  company_id bigint not null,
  plan_id bigint,
  rate_card_id bigint,
  channel varchar(32) not null,
  provider varchar(80),
  country varchar(2),
  carrier varchar(80),
  message_type varchar(32) not null,
  direction varchar(32) not null,
  quantity bigint not null,
  unit varchar(32) not null,
  unit_price_snapshot numeric(19, 6) not null,
  total_price numeric(19, 6) not null,
  currency varchar(3) not null,
  occurred_at timestamp(6) with time zone not null,
  created_at timestamp(6) with time zone not null
);

alter table companies add column if not exists business_email varchar(254);
alter table companies add column if not exists country varchar(80);
alter table companies add column if not exists timezone varchar(80);

create index if not exists idx_media_owner_user_id on media (owner_user_id);
create index if not exists idx_media_status on media (status);
create index if not exists idx_media_created_at on media (created_at);
create index if not exists idx_media_storage_provider on media (storage_provider);
create index if not exists idx_media_object_key on media (object_key);
create index if not exists idx_companies_status on companies (status);
create index if not exists idx_companies_created_by on companies (created_by_user_id);
create index if not exists idx_company_members_company_id on company_members (company_id);
create index if not exists idx_company_members_user_id on company_members (user_id);
create index if not exists idx_company_members_role on company_members (role);
create index if not exists idx_company_access_profiles_company on company_access_profiles (company_id);
create index if not exists idx_company_access_profile_permissions_profile on company_access_profile_permissions (profile_id);
create index if not exists idx_company_member_access_profile_grants_lookup on company_member_access_profile_grants (company_id, user_id);
create index if not exists idx_company_member_access_profile_grants_profile on company_member_access_profile_grants (profile_id);
create index if not exists idx_company_member_access_profile_grants_expires on company_member_access_profile_grants (expires_at);
create index if not exists idx_company_member_permission_overrides_lookup on company_member_permission_overrides (company_id, user_id, permission);
create index if not exists idx_company_member_permission_overrides_expires on company_member_permission_overrides (expires_at);
create index if not exists idx_company_role_permissions_company on company_role_permissions (company_id);
create index if not exists idx_company_role_permissions_role on company_role_permissions (role);
create index if not exists idx_platform_admins_role on platform_admins (role);
create index if not exists idx_admin_company_assignments_admin on admin_company_assignments (admin_user_id);
create index if not exists idx_admin_company_assignments_company on admin_company_assignments (company_id);
create index if not exists idx_plans_code_status on plans (code, status);
create index if not exists idx_plan_entitlements_plan_id on plan_entitlements (plan_id);
create index if not exists idx_company_subscriptions_company_status on company_subscriptions (company_id, status);
create index if not exists idx_company_subscriptions_plan_id on company_subscriptions (plan_id);
create index if not exists idx_rate_cards_plan_channel on rate_cards (plan_id, channel, status);
create index if not exists idx_rate_cards_effective on rate_cards (effective_from, effective_to);
create index if not exists idx_usage_events_company_time on usage_events (company_id, occurred_at);
create index if not exists idx_usage_events_channel on usage_events (channel);
