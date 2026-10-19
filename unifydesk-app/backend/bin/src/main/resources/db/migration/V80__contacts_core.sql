-- Contacts & Audience Management: B1 core schema.
-- All mutable domain tables carry company_id to make tenant scoping structural.

create table contacts (
  id bigint primary key,
  company_id bigint not null,
  first_name varchar(160),
  last_name varchar(160),
  display_name varchar(240),
  organization varchar(240),
  job_title varchar(160),
  country_code varchar(2),
  timezone varchar(80),
  language varchar(32),
  source varchar(80),
  notes text,
  owner_user_id bigint,
  status varchar(16) not null default 'ACTIVE',
  last_activity_at timestamp(6) with time zone,
  merged_into_contact_id bigint,
  created_by_user_id bigint not null,
  created_at timestamp(6) with time zone not null,
  updated_at timestamp(6) with time zone not null,
  deleted_at timestamp(6) with time zone,
  version bigint not null default 0,
  constraint fk_contacts_company
    foreign key (company_id) references companies(id) on delete restrict,
  constraint fk_contacts_owner
    foreign key (owner_user_id) references users(id) on delete set null,
  constraint fk_contacts_creator
    foreign key (created_by_user_id) references users(id) on delete restrict,
  constraint fk_contacts_merged_into
    foreign key (merged_into_contact_id) references contacts(id) on delete restrict,
  constraint ck_contacts_status
    check (status in ('ACTIVE','MERGED','DELETED')),
  constraint ck_contacts_version check (version >= 0),
  constraint ck_contacts_merge_state check (
    (status = 'MERGED' and merged_into_contact_id is not null and deleted_at is null)
    or (status <> 'MERGED' and merged_into_contact_id is null)
  ),
  constraint ck_contacts_delete_state check (
    (status = 'DELETED' and deleted_at is not null)
    or (status <> 'DELETED' and deleted_at is null)
  )
);

create index idx_contacts_company_id
  on contacts(company_id, id);
create index idx_contacts_company_updated
  on contacts(company_id, updated_at desc, id desc);
create index idx_contacts_company_created
  on contacts(company_id, created_at desc, id desc);
create index idx_contacts_company_status
  on contacts(company_id, status, id);
create index idx_contacts_company_display_name
  on contacts(company_id, lower(coalesce(display_name, '')) varchar_pattern_ops, id);
create index idx_contacts_company_first_name
  on contacts(company_id, lower(coalesce(first_name, '')) varchar_pattern_ops, id);
create index idx_contacts_company_last_name
  on contacts(company_id, lower(coalesce(last_name, '')) varchar_pattern_ops, id);
create index idx_contacts_company_organization
  on contacts(company_id, lower(coalesce(organization, '')) varchar_pattern_ops, id);

create table contact_identities (
  id bigint primary key,
  company_id bigint not null,
  contact_id bigint not null,
  identity_type varchar(16) not null,
  namespace varchar(120),
  value varchar(320) not null,
  normalized_value varchar(320) not null,
  is_primary boolean not null default false,
  status varchar(16) not null default 'ACTIVE',
  created_at timestamp(6) with time zone not null,
  updated_at timestamp(6) with time zone not null,
  deleted_at timestamp(6) with time zone,
  constraint fk_contact_identities_company
    foreign key (company_id) references companies(id) on delete restrict,
  constraint fk_contact_identities_contact
    foreign key (contact_id) references contacts(id) on delete cascade,
  constraint ck_contact_identities_type
    check (identity_type in ('EMAIL','PHONE','EXTERNAL')),
  constraint ck_contact_identities_status
    check (status in ('ACTIVE','DISABLED')),
  constraint ck_contact_identities_namespace check (
    (identity_type in ('EMAIL','PHONE') and namespace is null)
    or (identity_type = 'EXTERNAL' and namespace is not null and btrim(namespace) <> '')
  ),
  constraint ck_contact_identities_primary
    check (identity_type <> 'EXTERNAL' or is_primary = false),
  constraint ck_contact_identities_deleted check (
    (status = 'DISABLED' and deleted_at is not null)
    or (status = 'ACTIVE' and deleted_at is null)
  )
);

-- coalesce(namespace,'') is intentional: PostgreSQL UNIQUE otherwise treats NULLs
-- as distinct and would allow duplicate active EMAIL/PHONE identities.
create unique index uk_contact_identity_active
  on contact_identities(company_id, identity_type, coalesce(namespace, ''), normalized_value)
  where status = 'ACTIVE';
create unique index uk_contact_identity_primary_channel
  on contact_identities(contact_id, identity_type)
  where status = 'ACTIVE'
    and is_primary
    and identity_type in ('EMAIL','PHONE');
create index idx_contact_identities_contact
  on contact_identities(company_id, contact_id, status, identity_type, is_primary desc, created_at, id);
create index idx_contact_identities_lookup
  on contact_identities(company_id, identity_type, normalized_value, contact_id)
  where status = 'ACTIVE';
create index idx_contact_identities_prefix
  on contact_identities(company_id, normalized_value varchar_pattern_ops, contact_id)
  where status = 'ACTIVE';

create table contact_tags (
  id bigint primary key,
  company_id bigint not null,
  name varchar(120) not null,
  normalized_name varchar(120) not null,
  color varchar(32),
  metadata_json jsonb,
  created_at timestamp(6) with time zone not null,
  updated_at timestamp(6) with time zone not null,
  archived_at timestamp(6) with time zone,
  constraint fk_contact_tags_company
    foreign key (company_id) references companies(id) on delete restrict,
  constraint ck_contact_tags_metadata_size
    check (metadata_json is null or octet_length(metadata_json::text) <= 8192)
);

create unique index uk_contact_tags_active_name
  on contact_tags(company_id, normalized_name)
  where archived_at is null;
create index idx_contact_tags_company
  on contact_tags(company_id, archived_at, normalized_name, id);

create table contact_tag_memberships (
  company_id bigint not null,
  tag_id bigint not null,
  contact_id bigint not null,
  assigned_by_user_id bigint,
  assigned_at timestamp(6) with time zone not null,
  primary key (tag_id, contact_id),
  constraint fk_contact_tag_membership_company
    foreign key (company_id) references companies(id) on delete restrict,
  constraint fk_contact_tag_membership_tag
    foreign key (tag_id) references contact_tags(id) on delete cascade,
  constraint fk_contact_tag_membership_contact
    foreign key (contact_id) references contacts(id) on delete cascade,
  constraint fk_contact_tag_membership_actor
    foreign key (assigned_by_user_id) references users(id) on delete set null
);

create index idx_contact_tag_memberships_contact
  on contact_tag_memberships(company_id, contact_id, tag_id);
create index idx_contact_tag_memberships_tag
  on contact_tag_memberships(company_id, tag_id, contact_id);

create table contact_custom_field_definitions (
  id bigint primary key,
  company_id bigint not null,
  field_key varchar(64) not null,
  label varchar(120) not null,
  normalized_label varchar(120) not null,
  field_type varchar(24) not null,
  options_json jsonb,
  required boolean not null default false,
  position integer not null default 0,
  status varchar(16) not null default 'ACTIVE',
  created_at timestamp(6) with time zone not null,
  updated_at timestamp(6) with time zone not null,
  constraint fk_contact_custom_field_def_company
    foreign key (company_id) references companies(id) on delete restrict,
  constraint ck_contact_custom_field_type check (
    field_type in ('TEXT','NUMBER','BOOLEAN','DATE','DATETIME','SINGLE_SELECT','MULTI_SELECT')
  ),
  constraint ck_contact_custom_field_status
    check (status in ('ACTIVE','ARCHIVED')),
  constraint ck_contact_custom_field_required
    check (required = false),
  constraint ck_contact_custom_field_options_size
    check (options_json is null or octet_length(options_json::text) <= 16384),
  constraint uk_contact_custom_field_key unique(company_id, field_key)
);

create unique index uk_contact_custom_field_active_label
  on contact_custom_field_definitions(company_id, normalized_label)
  where status = 'ACTIVE';
create index idx_contact_custom_field_defs_company
  on contact_custom_field_definitions(company_id, status, position, id);

create table contact_custom_field_values (
  company_id bigint not null,
  contact_id bigint not null,
  field_id bigint not null,
  text_value text,
  number_value numeric,
  boolean_value boolean,
  date_value date,
  datetime_value timestamp(6) with time zone,
  text_array_value text[],
  updated_at timestamp(6) with time zone not null,
  primary key (contact_id, field_id),
  constraint fk_contact_custom_field_value_company
    foreign key (company_id) references companies(id) on delete restrict,
  constraint fk_contact_custom_field_value_contact
    foreign key (contact_id) references contacts(id) on delete cascade,
  constraint fk_contact_custom_field_value_field
    foreign key (field_id) references contact_custom_field_definitions(id) on delete restrict,
  constraint ck_contact_custom_field_one_value check (
    num_nonnulls(
      text_value,
      number_value,
      boolean_value,
      date_value,
      datetime_value,
      text_array_value
    ) = 1
  ),
  constraint ck_contact_custom_field_text_size
    check (text_value is null or length(text_value) <= 4000),
  constraint ck_contact_custom_field_array_size
    check (text_array_value is null or cardinality(text_array_value) <= 100)
);

create index idx_contact_custom_field_text
  on contact_custom_field_values(company_id, field_id, text_value, contact_id)
  where text_value is not null;
create index idx_contact_custom_field_number
  on contact_custom_field_values(company_id, field_id, number_value, contact_id)
  where number_value is not null;
create index idx_contact_custom_field_boolean
  on contact_custom_field_values(company_id, field_id, boolean_value, contact_id)
  where boolean_value is not null;
create index idx_contact_custom_field_date
  on contact_custom_field_values(company_id, field_id, date_value, contact_id)
  where date_value is not null;
create index idx_contact_custom_field_datetime
  on contact_custom_field_values(company_id, field_id, datetime_value, contact_id)
  where datetime_value is not null;
create index idx_contact_custom_field_multi_select
  on contact_custom_field_values using gin(text_array_value)
  where text_array_value is not null;
create index idx_contact_custom_field_field_contact
  on contact_custom_field_values(company_id, field_id, contact_id);

create table contact_lists (
  id bigint primary key,
  company_id bigint not null,
  name varchar(160) not null,
  normalized_name varchar(160) not null,
  description varchar(1000),
  status varchar(16) not null default 'ACTIVE',
  member_count bigint not null default 0,
  created_by_user_id bigint not null,
  created_at timestamp(6) with time zone not null,
  updated_at timestamp(6) with time zone not null,
  archived_at timestamp(6) with time zone,
  version bigint not null default 0,
  constraint fk_contact_lists_company
    foreign key (company_id) references companies(id) on delete restrict,
  constraint fk_contact_lists_creator
    foreign key (created_by_user_id) references users(id) on delete restrict,
  constraint ck_contact_lists_status
    check (status in ('ACTIVE','ARCHIVED')),
  constraint ck_contact_lists_member_count check (member_count >= 0),
  constraint ck_contact_lists_version check (version >= 0)
);

create unique index uk_contact_lists_active_name
  on contact_lists(company_id, normalized_name)
  where status = 'ACTIVE';
create index idx_contact_lists_company
  on contact_lists(company_id, status, updated_at desc, id desc);

create table contact_list_memberships (
  company_id bigint not null,
  list_id bigint not null,
  contact_id bigint not null,
  source varchar(16) not null,
  source_ref varchar(255),
  added_by_user_id bigint,
  added_at timestamp(6) with time zone not null,
  primary key (list_id, contact_id),
  constraint fk_contact_list_membership_company
    foreign key (company_id) references companies(id) on delete restrict,
  constraint fk_contact_list_membership_list
    foreign key (list_id) references contact_lists(id) on delete cascade,
  constraint fk_contact_list_membership_contact
    foreign key (contact_id) references contacts(id) on delete cascade,
  constraint fk_contact_list_membership_actor
    foreign key (added_by_user_id) references users(id) on delete set null,
  constraint ck_contact_list_membership_source
    check (source in ('MANUAL','IMPORT','API'))
);

create index idx_contact_list_memberships_contact
  on contact_list_memberships(company_id, contact_id, list_id);
create index idx_contact_list_memberships_list
  on contact_list_memberships(company_id, list_id, contact_id);

create table contact_merge_audit (
  id bigint primary key,
  company_id bigint not null,
  source_contact_id bigint not null,
  target_contact_id bigint not null,
  strategy_json jsonb not null,
  actor_user_id bigint not null,
  merged_at timestamp(6) with time zone not null,
  constraint fk_contact_merge_audit_company
    foreign key (company_id) references companies(id) on delete restrict,
  constraint fk_contact_merge_audit_source
    foreign key (source_contact_id) references contacts(id) on delete restrict,
  constraint fk_contact_merge_audit_target
    foreign key (target_contact_id) references contacts(id) on delete restrict,
  constraint fk_contact_merge_audit_actor
    foreign key (actor_user_id) references users(id) on delete restrict,
  constraint ck_contact_merge_strategy_size
    check (octet_length(strategy_json::text) <= 16384)
);

create index idx_contact_merge_audit_company
  on contact_merge_audit(company_id, merged_at desc, id desc);
create index idx_contact_merge_audit_source
  on contact_merge_audit(company_id, source_contact_id, merged_at desc);
create index idx_contact_merge_audit_target
  on contact_merge_audit(company_id, target_contact_id, merged_at desc);
