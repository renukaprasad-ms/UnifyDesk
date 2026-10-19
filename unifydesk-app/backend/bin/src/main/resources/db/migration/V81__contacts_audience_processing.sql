-- Contacts & Audience Round-1 B2 data plane.
-- This migration intentionally has no foreign keys to B1/V80 tables so the B2 worker
-- branch can migrate independently from the frozen V79 base. Lead V82 adds cross-worker FKs.

create table contact_segments (
  id bigint primary key,
  company_id bigint not null,
  name varchar(160) not null,
  normalized_name varchar(160) not null,
  description varchar(1000),
  filter_version integer not null,
  filter_json jsonb not null,
  filter_hash varchar(64) not null,
  status varchar(24) not null,
  cached_count bigint,
  count_computed_at timestamp(6) with time zone,
  created_by_user_id bigint not null,
  created_at timestamp(6) with time zone not null,
  updated_at timestamp(6) with time zone not null,
  version bigint not null default 0,
  constraint ck_contact_segments_status check (status in ('ACTIVE','ARCHIVED')),
  constraint ck_contact_segments_filter_version check (filter_version > 0),
  constraint ck_contact_segments_cached_count check (cached_count is null or cached_count >= 0)
);

create unique index uk_contact_segments_active_name
  on contact_segments (company_id, normalized_name)
  where status = 'ACTIVE';
create index idx_contact_segments_company_status
  on contact_segments (company_id, status, id);
create index idx_contact_segments_company_updated
  on contact_segments (company_id, updated_at desc, id desc);

create table contact_channel_preferences (
  id bigint primary key,
  company_id bigint not null,
  contact_id bigint not null,
  identity_id bigint,
  channel varchar(24) not null,
  purpose varchar(24) not null,
  state varchar(24) not null,
  source varchar(48) not null,
  source_ref varchar(255),
  occurred_at timestamp(6) with time zone not null,
  updated_at timestamp(6) with time zone not null,
  version bigint not null default 0,
  constraint ck_contact_channel_preferences_channel check (channel in ('EMAIL','SMS','WHATSAPP')),
  constraint ck_contact_channel_preferences_purpose check (purpose in ('MARKETING')),
  constraint ck_contact_channel_preferences_state check (state in ('UNKNOWN','OPTED_IN','OPTED_OUT'))
);

create unique index uk_contact_channel_preferences_key
  on contact_channel_preferences (
    company_id, contact_id, coalesce(identity_id, -1), channel, purpose
  );
create index idx_contact_channel_preferences_lookup
  on contact_channel_preferences (company_id, contact_id, channel, purpose, identity_id);

create table contact_preference_events (
  id bigint primary key,
  company_id bigint not null,
  contact_id bigint not null,
  identity_id bigint,
  channel varchar(24) not null,
  purpose varchar(24) not null,
  previous_state varchar(24),
  new_state varchar(24) not null,
  source varchar(48) not null,
  source_ref varchar(255),
  actor_type varchar(24) not null,
  actor_id varchar(160),
  reason varchar(500),
  occurred_at timestamp(6) with time zone not null,
  created_at timestamp(6) with time zone not null,
  idempotency_key varchar(160) not null,
  constraint ck_contact_preference_events_channel check (channel in ('EMAIL','SMS','WHATSAPP')),
  constraint ck_contact_preference_events_purpose check (purpose in ('MARKETING')),
  constraint ck_contact_preference_events_new_state check (new_state in ('UNKNOWN','OPTED_IN','OPTED_OUT')),
  constraint ck_contact_preference_events_actor check (actor_type in ('USER','CONTACT','IMPORT','API','PROVIDER','SYSTEM'))
);

create unique index uk_contact_preference_events_idempotency
  on contact_preference_events (company_id, idempotency_key);
create index idx_contact_preference_events_contact
  on contact_preference_events (company_id, contact_id, created_at desc, id desc);

create table contact_suppressions (
  id bigint primary key,
  company_id bigint not null,
  contact_id bigint,
  identity_id bigint,
  identity_fingerprint varchar(128),
  fingerprint_key_version integer,
  channel varchar(24) not null,
  reason_code varchar(48) not null,
  source varchar(48) not null,
  source_ref varchar(255),
  state varchar(24) not null,
  suppressed_at timestamp(6) with time zone not null,
  lifted_at timestamp(6) with time zone,
  created_at timestamp(6) with time zone not null,
  updated_at timestamp(6) with time zone not null,
  version bigint not null default 0,
  constraint ck_contact_suppressions_channel check (channel in ('EMAIL','SMS','WHATSAPP')),
  constraint ck_contact_suppressions_state check (state in ('ACTIVE','LIFTED')),
  constraint ck_contact_suppressions_reason check (
    reason_code in ('MANUAL','HARD_BOUNCE','COMPLAINT','PROVIDER_SUPPRESSED','DND',
                    'INVALID_DESTINATION','ABUSE_POLICY','LEGAL_POLICY')
  ),
  constraint ck_contact_suppressions_fingerprint check (
    identity_fingerprint is null or fingerprint_key_version is not null
  )
);

create index idx_contact_suppressions_contact_active
  on contact_suppressions (company_id, contact_id, channel, identity_id)
  where state = 'ACTIVE';
create index idx_contact_suppressions_fingerprint_active
  on contact_suppressions (company_id, channel, identity_fingerprint, fingerprint_key_version)
  where state = 'ACTIVE' and identity_fingerprint is not null;

create table contact_suppression_events (
  id bigint primary key,
  company_id bigint not null,
  suppression_id bigint not null,
  contact_id bigint,
  identity_id bigint,
  channel varchar(24) not null,
  reason_code varchar(48) not null,
  previous_state varchar(24),
  new_state varchar(24) not null,
  source varchar(48) not null,
  source_ref varchar(255),
  actor_type varchar(24) not null,
  actor_id varchar(160),
  reason varchar(500),
  occurred_at timestamp(6) with time zone not null,
  created_at timestamp(6) with time zone not null,
  idempotency_key varchar(160) not null,
  constraint ck_contact_suppression_events_state check (new_state in ('ACTIVE','LIFTED')),
  constraint ck_contact_suppression_events_actor check (actor_type in ('USER','CONTACT','IMPORT','API','PROVIDER','SYSTEM'))
);

create unique index uk_contact_suppression_events_idempotency
  on contact_suppression_events (company_id, idempotency_key);
create index idx_contact_suppression_events_suppression
  on contact_suppression_events (company_id, suppression_id, created_at desc, id desc);

create table contact_imports (
  id bigint primary key,
  company_id bigint not null,
  requested_by_user_id bigint not null,
  original_file_name varchar(255) not null,
  content_type varchar(128) not null,
  storage_provider varchar(24) not null,
  storage_bucket varchar(255) not null,
  storage_object_key varchar(1000) not null,
  expected_size_bytes bigint not null,
  content_sha256 varchar(64),
  mapping_json jsonb,
  mapping_hash varchar(64),
  duplicate_strategy varchar(24),
  target_list_id bigint,
  idempotency_key varchar(160),
  status varchar(32) not null,
  total_rows bigint not null default 0,
  processed_rows bigint not null default 0,
  succeeded_rows bigint not null default 0,
  failed_rows bigint not null default 0,
  processed_row_cursor bigint not null default 0,
  error_report_provider varchar(24),
  error_report_bucket varchar(255),
  error_report_object_key varchar(1000),
  error_report_size_bytes bigint,
  async_job_id bigint,
  created_at timestamp(6) with time zone not null,
  started_at timestamp(6) with time zone,
  completed_at timestamp(6) with time zone,
  expires_at timestamp(6) with time zone not null,
  constraint ck_contact_imports_status check (
    status in ('DRAFT_UPLOAD','UPLOADED','PREVIEWED','QUEUED','RUNNING','COMPLETED',
               'VALIDATION_FAILED','FAILED','EXPIRED')
  ),
  constraint ck_contact_imports_duplicate_strategy check (
    duplicate_strategy is null or duplicate_strategy in ('SKIP','MERGE_EXACT','FAIL')
  ),
  constraint ck_contact_imports_size check (expected_size_bytes >= 0),
  constraint ck_contact_imports_counters check (
    total_rows >= 0 and processed_rows >= 0 and succeeded_rows >= 0 and failed_rows >= 0
    and processed_row_cursor >= 0
  )
);

create unique index uk_contact_imports_idempotency
  on contact_imports (company_id, idempotency_key)
  where idempotency_key is not null;
create index idx_contact_imports_company_status
  on contact_imports (company_id, status, created_at desc, id desc);
create index idx_contact_imports_async_job
  on contact_imports (async_job_id)
  where async_job_id is not null;

create table contact_import_error_rows (
  import_id bigint not null,
  company_id bigint not null,
  row_number bigint not null,
  error_code varchar(64) not null,
  safe_message varchar(500) not null,
  created_at timestamp(6) with time zone not null,
  primary key (import_id, row_number, error_code),
  constraint ck_contact_import_error_row_number check (row_number > 0)
);

create index idx_contact_import_error_rows_page
  on contact_import_error_rows (company_id, import_id, row_number, error_code);

create table contact_exports (
  id bigint primary key,
  company_id bigint not null,
  requested_by_user_id bigint not null,
  source_type varchar(24) not null,
  source_id bigint,
  field_selection_json jsonb not null,
  idempotency_key varchar(160) not null,
  status varchar(24) not null,
  row_count bigint not null default 0,
  output_provider varchar(24),
  output_bucket varchar(255),
  output_object_key varchar(1000),
  output_size_bytes bigint,
  async_job_id bigint,
  created_at timestamp(6) with time zone not null,
  completed_at timestamp(6) with time zone,
  expires_at timestamp(6) with time zone not null,
  constraint ck_contact_exports_source check (source_type in ('CONTACTS','LIST','SEGMENT')),
  constraint ck_contact_exports_status check (status in ('QUEUED','RUNNING','COMPLETED','FAILED','EXPIRED')),
  constraint ck_contact_exports_row_count check (row_count >= 0)
);

create unique index uk_contact_exports_idempotency
  on contact_exports (company_id, idempotency_key);
create index idx_contact_exports_company_status
  on contact_exports (company_id, status, created_at desc, id desc);

create table contact_bulk_operations (
  id bigint primary key,
  company_id bigint not null,
  requested_by_user_id bigint not null,
  operation_type varchar(32) not null,
  selection_json jsonb not null,
  operation_json jsonb not null,
  idempotency_key varchar(160) not null,
  status varchar(24) not null,
  candidate_count bigint not null default 0,
  processed_count bigint not null default 0,
  succeeded_count bigint not null default 0,
  failed_count bigint not null default 0,
  cursor bigint not null default 0,
  async_job_id bigint,
  created_at timestamp(6) with time zone not null,
  completed_at timestamp(6) with time zone,
  constraint ck_contact_bulk_operation_type check (
    operation_type in ('ADD_TAG','REMOVE_TAG','ADD_TO_LIST','REMOVE_FROM_LIST','UPDATE_FIELD','DELETE')
  ),
  constraint ck_contact_bulk_status check (status in ('QUEUED','RUNNING','COMPLETED','FAILED')),
  constraint ck_contact_bulk_counters check (
    candidate_count >= 0 and processed_count >= 0 and succeeded_count >= 0 and failed_count >= 0 and cursor >= 0
  )
);

create unique index uk_contact_bulk_idempotency
  on contact_bulk_operations (company_id, idempotency_key);
create index idx_contact_bulk_company_status
  on contact_bulk_operations (company_id, status, created_at desc, id desc);

create table contact_activity_events (
  id bigint primary key,
  company_id bigint not null,
  contact_id bigint not null,
  event_type varchar(64) not null,
  actor_type varchar(24) not null,
  actor_id varchar(160),
  source_ref varchar(255),
  summary_json jsonb,
  occurred_at timestamp(6) with time zone not null,
  created_at timestamp(6) with time zone not null
);

create index idx_contact_activity_timeline
  on contact_activity_events (company_id, contact_id, occurred_at desc, id desc);

create table contact_audience_snapshots (
  id bigint primary key,
  company_id bigint not null,
  source_type varchar(24) not null,
  source_id bigint not null,
  source_version varchar(160) not null,
  filter_hash varchar(64),
  channel varchar(24) not null,
  purpose varchar(24) not null,
  snapshot_at timestamp(6) with time zone not null,
  status varchar(24) not null,
  candidate_count bigint not null default 0,
  eligible_count bigint not null default 0,
  cursor bigint not null default 0,
  async_job_id bigint,
  created_at timestamp(6) with time zone not null,
  completed_at timestamp(6) with time zone,
  constraint ck_contact_audience_snapshot_source check (source_type in ('LIST','SEGMENT')),
  constraint ck_contact_audience_snapshot_channel check (channel in ('EMAIL','SMS','WHATSAPP')),
  constraint ck_contact_audience_snapshot_purpose check (purpose in ('MARKETING')),
  constraint ck_contact_audience_snapshot_status check (status in ('PREPARING','READY','FAILED')),
  constraint ck_contact_audience_snapshot_counts check (candidate_count >= 0 and eligible_count >= 0 and cursor >= 0)
);

create index idx_contact_audience_snapshots_company
  on contact_audience_snapshots (company_id, status, created_at desc, id desc);

create table contact_audience_snapshot_members (
  snapshot_id bigint not null,
  company_id bigint not null,
  contact_id bigint not null,
  identity_id bigint not null,
  normalized_destination varchar(512) not null,
  display_name_snapshot varchar(255),
  eligibility_version varchar(64) not null,
  eligibility_reason varchar(64) not null,
  sequence_id bigint not null,
  created_at timestamp(6) with time zone not null,
  primary key (snapshot_id, contact_id)
);

create unique index uk_contact_audience_snapshot_destination
  on contact_audience_snapshot_members (snapshot_id, normalized_destination);
create unique index uk_contact_audience_snapshot_sequence
  on contact_audience_snapshot_members (snapshot_id, sequence_id);
create index idx_contact_audience_snapshot_members_company
  on contact_audience_snapshot_members (company_id, snapshot_id, sequence_id);
