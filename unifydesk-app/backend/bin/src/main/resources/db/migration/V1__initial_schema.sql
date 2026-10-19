-- Messaging database schema baseline.
-- This is the only Flyway migration for fresh installations.
-- No application/business data is seeded here except system metadata: permission definitions,
-- default role permission mappings, and the entitlement catalogue.
--
-- IMPORTANT: This file replaces the historical V1-V17 chain. Existing databases
-- that already contain Flyway history for the old migrations must be reset/rebaselined
-- before using this squashed migration.


-- ============================================================================
-- Consolidated from V1__initial_schema.sql
-- ============================================================================

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

-- ============================================================================
-- Consolidated from V3__seed_company_role_permissions.sql
-- ============================================================================

insert into company_role_permissions (id, company_id, role, permission, effect) values
  (100000000000001001, null, 'OWNER', 'COMPANY_VIEW', 'ALLOW'),
  (100000000000001002, null, 'OWNER', 'COMPANY_UPDATE', 'ALLOW'),
  (100000000000001003, null, 'OWNER', 'MEMBER_INVITE', 'ALLOW'),
  (100000000000001004, null, 'OWNER', 'MEMBER_REMOVE', 'ALLOW'),
  (100000000000001005, null, 'OWNER', 'MEMBER_ROLE_UPDATE', 'ALLOW'),
  (100000000000001006, null, 'OWNER', 'BILLING_VIEW', 'ALLOW'),
  (100000000000001007, null, 'OWNER', 'BILLING_MANAGE', 'ALLOW'),
  (100000000000001008, null, 'OWNER', 'BROADCAST_CREATE', 'ALLOW'),
  (100000000000001009, null, 'OWNER', 'BROADCAST_APPROVE', 'ALLOW'),
  (100000000000001010, null, 'OWNER', 'TEMPLATE_CREATE', 'ALLOW'),
  (100000000000001011, null, 'OWNER', 'TEMPLATE_APPROVE', 'ALLOW'),
  (100000000000001012, null, 'OWNER', 'MESSAGE_VIEW', 'ALLOW'),
  (100000000000001013, null, 'OWNER', 'MESSAGE_SEND', 'ALLOW'),
  (100000000000001014, null, 'OWNER', 'ADS_VIEW', 'ALLOW'),
  (100000000000001015, null, 'OWNER', 'ADS_MANAGE', 'ALLOW'),
  (100000000000001016, null, 'OWNER', 'SETTINGS_VIEW', 'ALLOW'),
  (100000000000001017, null, 'OWNER', 'SETTINGS_MANAGE', 'ALLOW'),
  (100000000000001018, null, 'OWNER', 'MEDIA_UPLOAD', 'ALLOW'),
  (100000000000001101, null, 'MANAGER', 'COMPANY_VIEW', 'ALLOW'),
  (100000000000001102, null, 'MANAGER', 'MEMBER_INVITE', 'ALLOW'),
  (100000000000001103, null, 'MANAGER', 'MESSAGE_VIEW', 'ALLOW'),
  (100000000000001104, null, 'MANAGER', 'MESSAGE_SEND', 'ALLOW'),
  (100000000000001105, null, 'MANAGER', 'BROADCAST_CREATE', 'ALLOW'),
  (100000000000001106, null, 'MANAGER', 'TEMPLATE_CREATE', 'ALLOW'),
  (100000000000001107, null, 'MANAGER', 'MEDIA_UPLOAD', 'ALLOW'),
  (100000000000001108, null, 'MANAGER', 'SETTINGS_VIEW', 'ALLOW'),
  (100000000000001201, null, 'MEMBER', 'COMPANY_VIEW', 'ALLOW'),
  (100000000000001202, null, 'MEMBER', 'MESSAGE_VIEW', 'ALLOW'),
  (100000000000001203, null, 'MEMBER', 'MESSAGE_SEND', 'ALLOW'),
  (100000000000001204, null, 'MEMBER', 'MEDIA_UPLOAD', 'ALLOW')
on conflict (id) do update set
  role = excluded.role,
  permission = excluded.permission,
  effect = excluded.effect;

-- ============================================================================
-- Consolidated from V4__ensure_admin_tables.sql
-- ============================================================================

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

create index if not exists idx_platform_admins_role on platform_admins (role);

create index if not exists idx_admin_company_assignments_admin on admin_company_assignments (admin_user_id);

create index if not exists idx_admin_company_assignments_company on admin_company_assignments (company_id);

-- ============================================================================
-- Consolidated from V5__add_company_logo.sql
-- ============================================================================

alter table companies add column if not exists logo_media_id bigint;

-- ============================================================================
-- Consolidated from V6__platform_admin_permissions.sql
-- ============================================================================

CREATE TABLE IF NOT EXISTS platform_admin_permissions (
    id BIGINT PRIMARY KEY,
    platform_admin_id BIGINT NOT NULL,
    permission VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_platform_admin_permissions_admin
      FOREIGN KEY (platform_admin_id) REFERENCES platform_admins(id) ON DELETE CASCADE,
    CONSTRAINT uk_platform_admin_permission UNIQUE (platform_admin_id, permission)
);

CREATE INDEX IF NOT EXISTS idx_platform_admin_permission_admin
  ON platform_admin_permissions(platform_admin_id);

-- ============================================================================
-- Consolidated from V7__channel_provider_foundation.sql
-- ============================================================================

create table if not exists channel_connections (
  id bigint primary key,
  company_id bigint not null,
  channel_type varchar(24) not null,
  provider_type varchar(40) not null,
  name varchar(120) not null,
  external_account_id varchar(255),
  status varchar(24) not null,
  created_by_user_id bigint not null,
  last_sync_at timestamp(6) with time zone,
  created_at timestamp(6) with time zone not null,
  updated_at timestamp(6) with time zone not null,
  constraint fk_channel_connections_company
    foreign key (company_id) references companies (id),
  constraint fk_channel_connections_created_by
    foreign key (created_by_user_id) references users (id),
  constraint uk_channel_connection_provider_account
    unique (company_id, provider_type, external_account_id)
);

create table if not exists sender_identities (
  id bigint primary key,
  connection_id bigint not null,
  identity_type varchar(32) not null,
  value varchar(320) not null,
  external_id varchar(255),
  status varchar(24) not null,
  is_default boolean not null default false,
  created_at timestamp(6) with time zone not null,
  updated_at timestamp(6) with time zone not null,
  constraint fk_sender_identities_connection
    foreign key (connection_id) references channel_connections (id) on delete cascade,
  constraint uk_sender_identity_connection_type_value
    unique (connection_id, identity_type, value)
);

create table if not exists provider_credentials (
  id bigint primary key,
  connection_id bigint not null,
  credential_type varchar(40) not null,
  encrypted_payload text not null,
  expires_at timestamp(6) with time zone,
  created_at timestamp(6) with time zone not null,
  updated_at timestamp(6) with time zone not null,
  constraint fk_provider_credentials_connection
    foreign key (connection_id) references channel_connections (id) on delete cascade,
  constraint uk_provider_credential_connection_type
    unique (connection_id, credential_type)
);

create index if not exists idx_channel_connections_company
  on channel_connections (company_id);

create index if not exists idx_channel_connections_company_channel_status
  on channel_connections (company_id, channel_type, status);

create index if not exists idx_channel_connections_provider_external
  on channel_connections (provider_type, external_account_id);

create index if not exists idx_sender_identity_connection
  on sender_identities (connection_id);

create index if not exists idx_sender_identity_connection_default
  on sender_identities (connection_id, is_default);

create index if not exists idx_sender_identity_connection_status
  on sender_identities (connection_id, status);

create unique index if not exists uk_sender_identity_default_per_connection
  on sender_identities (connection_id)
  where is_default = true;

create index if not exists idx_provider_credentials_connection
  on provider_credentials (connection_id);

create index if not exists idx_provider_credentials_expires_at
  on provider_credentials (expires_at);

-- ============================================================================
-- Consolidated from V8__company_iam_roles.sql
-- ============================================================================

create table if not exists permissions (
  permission_key varchar(120) primary key,
  resource varchar(80) not null,
  action varchar(80) not null,
  description varchar(255) not null,
  assignable boolean not null default true
);

create table if not exists company_custom_roles (
  id bigint primary key,
  company_id bigint not null,
  name varchar(120) not null,
  description varchar(500),
  system_role boolean not null default false,
  created_by_user_id bigint not null,
  created_at timestamp(6) with time zone not null,
  updated_at timestamp(6) with time zone not null,
  constraint fk_company_custom_roles_company
    foreign key (company_id) references companies(id) on delete cascade,
  constraint fk_company_custom_roles_created_by
    foreign key (created_by_user_id) references users(id)
);

create unique index if not exists uk_company_custom_roles_name_ci
  on company_custom_roles (company_id, lower(name));

create index if not exists idx_company_custom_roles_company
  on company_custom_roles (company_id);

create table if not exists company_custom_role_permissions (
  id bigint primary key,
  role_id bigint not null,
  permission_key varchar(120) not null,
  constraint uk_company_custom_role_permission unique (role_id, permission_key),
  constraint fk_company_custom_role_permissions_role
    foreign key (role_id) references company_custom_roles(id) on delete cascade,
  constraint fk_company_custom_role_permissions_permission
    foreign key (permission_key) references permissions(permission_key)
);

create index if not exists idx_company_custom_role_permissions_role
  on company_custom_role_permissions (role_id);

create index if not exists idx_company_custom_role_permissions_key
  on company_custom_role_permissions (permission_key);

create table if not exists company_member_role_assignments (
  id bigint primary key,
  company_id bigint not null,
  user_id bigint not null,
  role_id bigint not null,
  assigned_by_user_id bigint not null,
  expires_at timestamp(6) with time zone,
  revoked_at timestamp(6) with time zone,
  created_at timestamp(6) with time zone not null,
  updated_at timestamp(6) with time zone not null,
  constraint uk_company_member_role_assignment unique (company_id, user_id, role_id),
  constraint fk_company_member_role_assignments_company
    foreign key (company_id) references companies(id) on delete cascade,
  constraint fk_company_member_role_assignments_user
    foreign key (user_id) references users(id),
  constraint fk_company_member_role_assignments_role
    foreign key (role_id) references company_custom_roles(id) on delete cascade,
  constraint fk_company_member_role_assignments_assigned_by
    foreign key (assigned_by_user_id) references users(id)
);

create index if not exists idx_company_member_role_assignments_member
  on company_member_role_assignments (company_id, user_id);

create index if not exists idx_company_member_role_assignments_role
  on company_member_role_assignments (role_id);

create index if not exists idx_company_member_role_assignments_expires
  on company_member_role_assignments (expires_at);

insert into permissions (permission_key, resource, action, description, assignable) values
  ('company.read', 'company', 'read', 'View company information', true),
  ('company.modify', 'company', 'modify', 'Modify company information', true),
  ('company.delete', 'company', 'delete', 'Delete the company', false),
  ('company.transfer_ownership', 'company', 'transfer_ownership', 'Transfer company ownership', false),

  ('user.read', 'user', 'read', 'View company members', true),
  ('user.invite', 'user', 'invite', 'Invite company members', true),
  ('user.modify', 'user', 'modify', 'Modify company members', true),
  ('user.remove', 'user', 'remove', 'Remove company members', true),

  ('role.read', 'role', 'read', 'View roles and permission definitions', true),
  ('role.create', 'role', 'create', 'Create company roles', true),
  ('role.modify', 'role', 'modify', 'Modify company roles', true),
  ('role.delete', 'role', 'delete', 'Delete company roles', true),
  ('role.assign', 'role', 'assign', 'Assign and revoke company roles', true),

  ('email.connect', 'email', 'connect', 'Connect an email account', true),
  ('email.disconnect', 'email', 'disconnect', 'Disconnect an email account', true),
  ('email.read', 'email', 'read', 'Read email conversations and account configuration', true),
  ('email.send', 'email', 'send', 'Send email', true),
  ('email.modify', 'email', 'modify', 'Modify email configuration and messages', true),

  ('whatsapp.connect', 'whatsapp', 'connect', 'Connect a WhatsApp account', true),
  ('whatsapp.disconnect', 'whatsapp', 'disconnect', 'Disconnect a WhatsApp account', true),
  ('whatsapp.read', 'whatsapp', 'read', 'Read WhatsApp conversations and configuration', true),
  ('whatsapp.send', 'whatsapp', 'send', 'Send WhatsApp messages', true),
  ('whatsapp.modify', 'whatsapp', 'modify', 'Modify WhatsApp configuration and messages', true),
  ('whatsapp.template.read', 'whatsapp.template', 'read', 'View WhatsApp templates', true),
  ('whatsapp.template.create', 'whatsapp.template', 'create', 'Create WhatsApp templates', true),
  ('whatsapp.template.modify', 'whatsapp.template', 'modify', 'Modify WhatsApp templates', true),
  ('whatsapp.template.delete', 'whatsapp.template', 'delete', 'Delete WhatsApp templates', true),

  ('sms.connect', 'sms', 'connect', 'Connect an SMS provider', true),
  ('sms.disconnect', 'sms', 'disconnect', 'Disconnect an SMS provider', true),
  ('sms.read', 'sms', 'read', 'View SMS configuration and message history', true),
  ('sms.send', 'sms', 'send', 'Send SMS messages', true),
  ('sms.modify', 'sms', 'modify', 'Modify SMS configuration', true),
  ('sms.sender.read', 'sms.sender', 'read', 'View SMS sender identities', true),
  ('sms.sender.create', 'sms.sender', 'create', 'Create SMS sender identities', true),
  ('sms.sender.modify', 'sms.sender', 'modify', 'Modify SMS sender identities', true),
  ('sms.sender.delete', 'sms.sender', 'delete', 'Delete SMS sender identities', true),

  ('broadcast.read', 'broadcast', 'read', 'View broadcasts', true),
  ('broadcast.create', 'broadcast', 'create', 'Create broadcasts', true),
  ('broadcast.send', 'broadcast', 'send', 'Send broadcasts', true),
  ('broadcast.modify', 'broadcast', 'modify', 'Modify broadcasts', true),
  ('broadcast.delete', 'broadcast', 'delete', 'Delete broadcasts', true),

  ('template.read', 'template', 'read', 'View templates', true),
  ('template.create', 'template', 'create', 'Create templates', true),
  ('template.modify', 'template', 'modify', 'Modify templates', true),
  ('template.delete', 'template', 'delete', 'Delete templates', true),

  ('billing.read', 'billing', 'read', 'View billing information', true),
  ('billing.balance.read', 'billing.balance', 'read', 'View account balance', true),
  ('billing.balance.add', 'billing.balance', 'add', 'Add account balance', true),
  ('billing.card.read', 'billing.card', 'read', 'View payment methods', true),
  ('billing.card.add', 'billing.card', 'add', 'Add payment methods', true),
  ('billing.card.modify', 'billing.card', 'modify', 'Modify payment methods', true),
  ('billing.card.delete', 'billing.card', 'delete', 'Delete payment methods', true),
  ('billing.invoice.read', 'billing.invoice', 'read', 'View invoices', true),
  ('billing.invoice.download', 'billing.invoice', 'download', 'Download invoices', true),
  ('billing.subscription.read', 'billing.subscription', 'read', 'View subscription details', true),
  ('billing.subscription.modify', 'billing.subscription', 'modify', 'Modify subscription details', true),
  ('billing.subscription.cancel', 'billing.subscription', 'cancel', 'Cancel a subscription', true),

  ('ads.read', 'ads', 'read', 'View ad accounts and campaigns', true),
  ('ads.create', 'ads', 'create', 'Create advertising campaigns', true),
  ('ads.modify', 'ads', 'modify', 'Modify advertising campaigns', true),
  ('ads.delete', 'ads', 'delete', 'Delete advertising campaigns', true),
  ('ads.balance.add', 'ads.balance', 'add', 'Add advertising provider balance', true),

  ('media.read', 'media', 'read', 'View company media', true),
  ('media.upload', 'media', 'upload', 'Upload company media', true),
  ('media.delete', 'media', 'delete', 'Delete company media', true),

  ('settings.read', 'settings', 'read', 'View company settings', true),
  ('settings.modify', 'settings', 'modify', 'Modify company settings', true)
on conflict (permission_key) do update set
  resource = excluded.resource,
  action = excluded.action,
  description = excluded.description,
  assignable = excluded.assignable;

-- ============================================================================
-- Consolidated from V9__billing_catalog_v2.sql
-- ============================================================================

-- Billing catalogue v2: versioned plan prices, typed entitlements, pricing overrides,
-- subscription lifecycle and idempotent usage snapshots.

alter table plans add column if not exists description varchar(500);

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

-- System entitlement catalogue. These are definitions/default fallbacks only.
-- Plan-specific values are configured through plan_entitlements by Super Admin.
insert into entitlement_definitions
  (id, entitlement_key, name, description, value_type, category, default_value, active, created_at, updated_at)
values
  (910000000000000001, 'MAX_WORKSPACE_USERS', 'Workspace users', 'Maximum active users in the workspace, including the owner', 'INTEGER', 'WORKSPACE', '1', true, now(), now()),
  (910000000000000002, 'CONTACT_LIMIT', 'Contacts', 'Maximum contacts stored in the workspace', 'INTEGER', 'WORKSPACE', '0', true, now(), now()),
  (910000000000000003, 'SEGMENT_LIMIT', 'Segments', 'Maximum audience segments in the workspace', 'INTEGER', 'WORKSPACE', '0', true, now(), now()),
  (910000000000000004, 'BROADCAST_GROUP_LIMIT', 'Broadcast groups', 'Maximum reusable broadcast recipient groups', 'INTEGER', 'WORKSPACE', '0', true, now(), now()),

  (910000000000000005, 'EMAIL_ACCOUNTS', 'Email connections', 'Maximum connected email accounts', 'INTEGER', 'CHANNEL', '0', true, now(), now()),
  (910000000000000006, 'WHATSAPP_ACCOUNTS', 'WhatsApp connections', 'Maximum connected WhatsApp Business accounts', 'INTEGER', 'CHANNEL', '0', true, now(), now()),
  (910000000000000007, 'WHATSAPP_NUMBERS', 'WhatsApp numbers', 'Maximum WhatsApp sender phone numbers', 'INTEGER', 'CHANNEL', '0', true, now(), now()),
  (910000000000000008, 'SMS_ACCOUNTS', 'SMS connections', 'Maximum connected SMS provider accounts', 'INTEGER', 'CHANNEL', '0', true, now(), now()),
  (910000000000000009, 'SMS_SENDER_IDS', 'SMS sender IDs', 'Maximum SMS sender IDs or headers', 'INTEGER', 'CHANNEL', '0', true, now(), now()),

  (910000000000000010, 'BROADCAST_BATCH_LIMIT', 'Recipients per broadcast', 'Maximum recipients in a single broadcast', 'INTEGER', 'BROADCAST', '0', true, now(), now()),
  (910000000000000011, 'BROADCASTS_PER_DAY', 'Broadcasts per day', 'Maximum broadcasts sent per calendar day', 'INTEGER', 'BROADCAST', '0', true, now(), now()),
  (910000000000000012, 'BROADCASTS_PER_MONTH', 'Broadcasts per month', 'Maximum broadcasts sent per billing month', 'INTEGER', 'BROADCAST', '0', true, now(), now()),
  (910000000000000013, 'BROADCAST_RECIPIENTS_PER_DAY', 'Broadcast recipients per day', 'Maximum total broadcast recipients reached per calendar day', 'INTEGER', 'BROADCAST', '0', true, now(), now()),
  (910000000000000014, 'BROADCAST_RECIPIENTS_PER_MONTH', 'Broadcast recipients per month', 'Maximum total broadcast recipients reached per billing month', 'INTEGER', 'BROADCAST', '0', true, now(), now()),
  (910000000000000015, 'SCHEDULED_BROADCASTS_ENABLED', 'Scheduled broadcasts', 'Allow broadcasts to be scheduled for later delivery', 'BOOLEAN', 'BROADCAST', 'false', true, now(), now()),

  (910000000000000016, 'EMAIL_TEMPLATE_LIMIT', 'Email templates', 'Maximum saved email templates', 'INTEGER', 'TEMPLATE', '0', true, now(), now()),
  (910000000000000017, 'WHATSAPP_TEMPLATE_LIMIT', 'WhatsApp templates', 'Maximum saved WhatsApp templates managed by the workspace', 'INTEGER', 'TEMPLATE', '0', true, now(), now()),
  (910000000000000018, 'SMS_TEMPLATE_LIMIT', 'SMS templates', 'Maximum saved SMS templates', 'INTEGER', 'TEMPLATE', '0', true, now(), now()),

  (910000000000000019, 'STORAGE_GB', 'Storage', 'Maximum private workspace storage in GiB; use -1 for unlimited', 'DECIMAL', 'STORAGE', '0', true, now(), now()),

  (910000000000000020, 'AUTO_REPLY_ENABLED', 'Auto reply', 'Enable automated replies', 'BOOLEAN', 'FEATURE', 'false', true, now(), now()),
  (910000000000000021, 'USER_SIGNATURE_ENABLED', 'User signatures', 'Enable per-user message signatures', 'BOOLEAN', 'FEATURE', 'false', true, now(), now()),
  (910000000000000022, 'API_ACCESS_ENABLED', 'API access', 'Enable customer API access', 'BOOLEAN', 'FEATURE', 'false', true, now(), now())
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

alter table company_subscriptions add column if not exists plan_price_id bigint;

alter table company_subscriptions add column if not exists current_period_start timestamp(6) with time zone;

alter table company_subscriptions add column if not exists current_period_end timestamp(6) with time zone;

alter table company_subscriptions add column if not exists trial_ends_at timestamp(6) with time zone;

alter table company_subscriptions add column if not exists cancel_at_period_end boolean not null default false;

alter table company_subscriptions add column if not exists cancelled_at timestamp(6) with time zone;

create unique index if not exists uk_company_subscriptions_current
  on company_subscriptions (company_id)
  where status in ('ACTIVE', 'TRIALING', 'PAST_DUE');

create index if not exists idx_company_subscriptions_plan_price on company_subscriptions (plan_price_id);

alter table company_subscriptions
  drop constraint if exists fk_company_subscriptions_plan_price;

alter table company_subscriptions
  add constraint fk_company_subscriptions_plan_price
  foreign key (plan_price_id) references plan_prices(id);

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

alter table usage_events add column if not exists pricing_rule_id bigint;

alter table usage_events add column if not exists pricing_source varchar(32);

alter table usage_events add column if not exists source_type varchar(64);

alter table usage_events add column if not exists source_id varchar(160);

alter table usage_events add column if not exists idempotency_key varchar(255);

alter table usage_events alter column source_type set not null;

alter table usage_events alter column source_id set not null;

alter table usage_events alter column idempotency_key set not null;

create unique index if not exists uk_usage_events_idempotency on usage_events (idempotency_key);

create index if not exists idx_usage_events_source on usage_events (source_type, source_id);

-- ============================================================================
-- Consolidated from V12__company_invitations.sql
-- ============================================================================

create table if not exists company_invitations (
  id bigint primary key,
  company_id bigint not null,
  email varchar(320) not null,
  status varchar(32) not null,
  token_hash varchar(64) not null,
  invited_by_user_id bigint not null,
  expires_at timestamp(6) with time zone not null,
  accepted_at timestamp(6) with time zone,
  accepted_by_user_id bigint,
  created_at timestamp(6) with time zone not null,
  updated_at timestamp(6) with time zone not null,
  constraint fk_company_invitations_company
    foreign key (company_id) references companies(id),
  constraint fk_company_invitations_invited_by
    foreign key (invited_by_user_id) references users(id),
  constraint fk_company_invitations_accepted_by
    foreign key (accepted_by_user_id) references users(id),
  constraint uk_company_invitations_token_hash unique (token_hash)
);

create table if not exists company_invitation_roles (
  invitation_id bigint not null,
  role_id bigint not null,
  primary key (invitation_id, role_id),
  constraint fk_company_invitation_roles_invitation
    foreign key (invitation_id) references company_invitations(id) on delete cascade,
  constraint fk_company_invitation_roles_role
    foreign key (role_id) references company_custom_roles(id) on delete cascade
);

create index if not exists idx_company_invitations_company_status
  on company_invitations (company_id, status);

create index if not exists idx_company_invitations_email
  on company_invitations (lower(email));

create index if not exists idx_company_invitations_expires
  on company_invitations (expires_at);

create unique index if not exists uk_company_invitations_pending_email
  on company_invitations (company_id, lower(email))
  where status = 'PENDING';

create index if not exists idx_company_invitation_roles_role
  on company_invitation_roles (role_id);

-- ============================================================================
-- Consolidated from V13__inbox_foundation.sql
-- ============================================================================

create table if not exists inbox_conversations (
  id bigint primary key,
  company_id bigint not null,
  connection_id bigint not null,
  provider_type varchar(40) not null,
  external_conversation_id varchar(255) not null,
  subject varchar(998),
  last_message_preview text,
  last_message_at timestamp(6) with time zone,
  unread_count integer not null default 0,
  status varchar(24) not null,
  created_at timestamp(6) with time zone not null,
  updated_at timestamp(6) with time zone not null,
  constraint fk_inbox_conversations_company foreign key (company_id) references companies (id) on delete cascade,
  constraint fk_inbox_conversations_connection foreign key (connection_id) references channel_connections (id) on delete cascade,
  constraint uk_inbox_conversation_external unique (connection_id, external_conversation_id)
);

create table if not exists inbox_participants (
  id bigint primary key,
  conversation_id bigint not null,
  participant_type varchar(32) not null,
  address varchar(320) not null,
  display_name varchar(255),
  created_at timestamp(6) with time zone not null,
  updated_at timestamp(6) with time zone not null,
  constraint fk_inbox_participants_conversation foreign key (conversation_id) references inbox_conversations (id) on delete cascade,
  constraint uk_inbox_participant_address unique (conversation_id, participant_type, address)
);

create table if not exists inbox_messages (
  id bigint primary key,
  conversation_id bigint not null,
  connection_id bigint not null,
  external_message_id varchar(255) not null,
  internet_message_id varchar(998),
  provider_history_id varchar(255),
  direction varchar(24) not null,
  status varchar(24) not null,
  subject varchar(998),
  snippet text,
  body_text text,
  sent_at timestamp(6) with time zone,
  is_read boolean not null default true,
  created_at timestamp(6) with time zone not null,
  updated_at timestamp(6) with time zone not null,
  constraint fk_inbox_messages_conversation foreign key (conversation_id) references inbox_conversations (id) on delete cascade,
  constraint fk_inbox_messages_connection foreign key (connection_id) references channel_connections (id) on delete cascade,
  constraint uk_inbox_message_external unique (connection_id, external_message_id)
);

create table if not exists inbox_message_attachments (
  id bigint primary key,
  message_id bigint not null,
  external_attachment_id varchar(255) not null,
  filename varchar(512),
  mime_type varchar(255),
  size_bytes bigint,
  created_at timestamp(6) with time zone not null,
  updated_at timestamp(6) with time zone not null,
  constraint fk_inbox_message_attachments_message foreign key (message_id) references inbox_messages (id) on delete cascade,
  constraint uk_inbox_message_attachment_external unique (message_id, external_attachment_id)
);

create table if not exists channel_sync_states (
  id bigint primary key,
  connection_id bigint not null,
  provider_type varchar(40) not null,
  cursor_value varchar(1024),
  last_full_sync_at timestamp(6) with time zone,
  last_incremental_sync_at timestamp(6) with time zone,
  created_at timestamp(6) with time zone not null,
  updated_at timestamp(6) with time zone not null,
  constraint fk_channel_sync_states_connection foreign key (connection_id) references channel_connections (id) on delete cascade,
  constraint uk_channel_sync_state_connection_provider unique (connection_id, provider_type)
);

create index if not exists idx_inbox_conversations_company_last_message on inbox_conversations (company_id, last_message_at desc);

create index if not exists idx_inbox_conversations_connection on inbox_conversations (connection_id);

create index if not exists idx_inbox_messages_conversation_sent on inbox_messages (conversation_id, sent_at);

create index if not exists idx_inbox_messages_connection on inbox_messages (connection_id);

create index if not exists idx_inbox_messages_unread on inbox_messages (conversation_id, is_read) where is_read = false;

create index if not exists idx_inbox_participants_conversation on inbox_participants (conversation_id);

create index if not exists idx_inbox_attachments_message on inbox_message_attachments (message_id);

create index if not exists idx_channel_sync_states_connection on channel_sync_states (connection_id);

-- ============================================================================
-- Consolidated from V14__inbox_message_html_body.sql
-- ============================================================================

alter table inbox_messages
  add column if not exists body_html text;

-- ============================================================================
-- Consolidated from V15__soft_delete_lifecycle.sql
-- ============================================================================

alter table users add column if not exists status varchar(24) not null default 'ACTIVE';

alter table users add column if not exists deleted_at timestamp(6) with time zone;

create index if not exists idx_users_status on users(status);

alter table company_custom_roles add column if not exists deleted_at timestamp(6) with time zone;

alter table company_custom_roles add column if not exists deleted_by_user_id bigint;

create index if not exists idx_company_custom_roles_active
  on company_custom_roles(company_id, deleted_at);

drop index if exists uk_company_custom_roles_name_ci;

create unique index if not exists uk_company_custom_roles_name_ci
  on company_custom_roles(company_id, lower(name))
  where deleted_at is null;

-- ============================================================================
-- Consolidated from V16__async_jobs.sql
-- ============================================================================

create table if not exists async_jobs (
  id bigint primary key,
  company_id bigint,
  user_id bigint not null,
  job_type varchar(48) not null,
  status varchar(24) not null,
  total_units bigint not null default 0,
  completed_units bigint not null default 0,
  progress_percent integer not null default 0,
  status_message varchar(500),
  metadata_json text,
  error_message varchar(1000),
  created_at timestamp(6) with time zone not null,
  started_at timestamp(6) with time zone,
  completed_at timestamp(6) with time zone,
  updated_at timestamp(6) with time zone not null,
  constraint fk_async_jobs_company foreign key (company_id) references companies(id) on delete cascade,
  constraint fk_async_jobs_user foreign key (user_id) references users(id)
);

create index if not exists idx_async_jobs_user_created on async_jobs(user_id, created_at desc);

create index if not exists idx_async_jobs_company_status on async_jobs(company_id, status);

-- ============================================================================
-- Consolidated from V17__storage_entitlement.sql
-- ============================================================================

create index if not exists idx_media_company_status on media(company_id, status);
