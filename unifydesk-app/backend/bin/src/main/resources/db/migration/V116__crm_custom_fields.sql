-- CRM Custom Fields R1
-- Admins define per-entity field schemas; values are stored as JSONB on the parent row.
-- Supported entity types: LEAD, DEAL, ACCOUNT

create table crm_custom_field_defs (
  id          bigint primary key,
  company_id  bigint not null references companies(id) on delete cascade,
  entity_type varchar(12) not null check (entity_type in ('LEAD','DEAL','ACCOUNT')),
  field_key   varchar(64) not null,
  label       varchar(120) not null,
  field_type  varchar(16) not null check (field_type in ('TEXT','NUMBER','DATE','BOOLEAN','DROPDOWN','MULTI_SELECT','URL')),
  options     jsonb,                        -- non-null for DROPDOWN / MULTI_SELECT: ["Opt A","Opt B"]
  required    boolean not null default false,
  display_order int not null default 0,
  created_by_user_id bigint references users(id) on delete set null,
  created_at  timestamptz not null default now(),
  updated_at  timestamptz not null default now(),
  constraint ck_crm_field_label check (btrim(label) <> ''),
  constraint ck_crm_field_key   check (field_key ~ '^[a-z][a-z0-9_]{0,62}$'),
  -- options required for DROPDOWN/MULTI_SELECT, forbidden otherwise
  constraint ck_crm_field_options check (
    (field_type in ('DROPDOWN','MULTI_SELECT') and options is not null)
    or (field_type not in ('DROPDOWN','MULTI_SELECT') and options is null)
  ),
  constraint uk_crm_field_key unique (company_id, entity_type, field_key)
);
create index idx_crm_custom_field_defs_list on crm_custom_field_defs(company_id, entity_type, display_order, id);

-- Add custom_fields JSONB column to the three CRM entity tables.
alter table crm_leads    add column if not exists custom_fields jsonb not null default '{}';
alter table crm_deals    add column if not exists custom_fields jsonb not null default '{}';
alter table crm_accounts add column if not exists custom_fields jsonb not null default '{}';

-- GIN indexes allow fast `@>` containment queries on custom field values.
create index idx_crm_leads_custom    on crm_leads    using gin (custom_fields) where custom_fields <> '{}';
create index idx_crm_deals_custom    on crm_deals    using gin (custom_fields) where custom_fields <> '{}';
create index idx_crm_accounts_custom on crm_accounts using gin (custom_fields) where custom_fields <> '{}';
