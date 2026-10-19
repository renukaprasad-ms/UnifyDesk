create table broadcast_groups (
  id bigint primary key,
  company_id bigint not null,
  channel_type varchar(24) not null,
  name varchar(160) not null,
  description varchar(500),
  member_count bigint not null default 0,
  created_by_user_id bigint not null,
  version bigint not null default 0,
  created_at timestamp(6) with time zone not null,
  updated_at timestamp(6) with time zone not null,
  constraint fk_broadcast_groups_company foreign key (company_id) references companies (id) on delete cascade,
  constraint fk_broadcast_groups_created_by foreign key (created_by_user_id) references users (id)
);

create index idx_broadcast_groups_company_channel_updated
  on broadcast_groups (company_id, channel_type, updated_at desc);

create table broadcast_group_members (
  id bigint primary key,
  group_id bigint not null,
  company_id bigint not null,
  destination varchar(320) not null,
  normalized_destination varchar(320) not null,
  display_name varchar(255),
  metadata_json text,
  status varchar(24) not null,
  created_at timestamp(6) with time zone not null,
  updated_at timestamp(6) with time zone not null,
  constraint fk_broadcast_group_members_group foreign key (group_id) references broadcast_groups (id) on delete cascade,
  constraint fk_broadcast_group_members_company foreign key (company_id) references companies (id) on delete cascade,
  constraint uk_broadcast_group_member_destination unique (group_id, normalized_destination)
);

create index idx_broadcast_group_members_keyset
  on broadcast_group_members (group_id, status, id);

create index idx_broadcast_group_members_company_destination
  on broadcast_group_members (company_id, normalized_destination);

create table broadcast_messages (
  id bigint primary key,
  company_id bigint not null,
  group_id bigint not null,
  connection_id bigint not null,
  channel_type varchar(24) not null,
  subject varchar(998),
  body_text text not null,
  status varchar(32) not null,
  scheduled_at timestamp(6) with time zone,
  created_by_user_id bigint not null,
  version bigint not null default 0,
  created_at timestamp(6) with time zone not null,
  updated_at timestamp(6) with time zone not null,
  constraint fk_broadcast_messages_company foreign key (company_id) references companies (id) on delete cascade,
  constraint fk_broadcast_messages_group foreign key (group_id) references broadcast_groups (id) on delete cascade,
  constraint fk_broadcast_messages_connection foreign key (connection_id) references channel_connections (id),
  constraint fk_broadcast_messages_created_by foreign key (created_by_user_id) references users (id)
);

create index idx_broadcast_messages_group_created
  on broadcast_messages (group_id, created_at desc);

create index idx_broadcast_messages_due
  on broadcast_messages (scheduled_at, id)
  where status = 'SCHEDULED';

create table broadcast_runs (
  id bigint primary key,
  broadcast_message_id bigint not null,
  company_id bigint not null,
  group_id bigint not null,
  connection_id bigint not null,
  provider_type varchar(40) not null,
  status varchar(32) not null,
  subject_snapshot varchar(998),
  body_text_snapshot text not null,
  snapshot_cursor_member_id bigint,
  snapshot_complete boolean not null default false,
  total_recipients bigint not null default 0,
  batch_count integer not null default 0,
  started_at timestamp(6) with time zone,
  completed_at timestamp(6) with time zone,
  claimed_at timestamp(6) with time zone,
  version bigint not null default 0,
  created_at timestamp(6) with time zone not null,
  updated_at timestamp(6) with time zone not null,
  constraint fk_broadcast_runs_message foreign key (broadcast_message_id) references broadcast_messages (id) on delete cascade,
  constraint fk_broadcast_runs_company foreign key (company_id) references companies (id) on delete cascade,
  constraint fk_broadcast_runs_group foreign key (group_id) references broadcast_groups (id) on delete cascade,
  constraint fk_broadcast_runs_connection foreign key (connection_id) references channel_connections (id),
  constraint uk_broadcast_run_message unique (broadcast_message_id)
);

create index idx_broadcast_runs_company_status
  on broadcast_runs (company_id, status, updated_at desc);

create table broadcast_batches (
  id bigint primary key,
  run_id bigint not null,
  company_id bigint not null,
  sequence_no integer not null,
  status varchar(24) not null,
  recipient_count integer not null default 0,
  processed_count integer not null default 0,
  attempt_count integer not null default 0,
  next_attempt_at timestamp(6) with time zone,
  claimed_at timestamp(6) with time zone,
  last_error varchar(1000),
  created_at timestamp(6) with time zone not null,
  updated_at timestamp(6) with time zone not null,
  constraint fk_broadcast_batches_run foreign key (run_id) references broadcast_runs (id) on delete cascade,
  constraint fk_broadcast_batches_company foreign key (company_id) references companies (id) on delete cascade,
  constraint uk_broadcast_batch_sequence unique (run_id, sequence_no)
);

create index idx_broadcast_batches_claim
  on broadcast_batches (status, next_attempt_at, id);

create table broadcast_recipients (
  id bigint primary key,
  run_id bigint not null,
  batch_id bigint not null,
  company_id bigint not null,
  connection_id bigint not null,
  source_member_id bigint not null,
  destination varchar(320) not null,
  normalized_destination varchar(320) not null,
  display_name varchar(255),
  metadata_json text,
  status varchar(32) not null,
  attempt_count integer not null default 0,
  provider_message_id varchar(512),
  internet_message_id varchar(998),
  provider_correlation_id varchar(128) not null,
  next_attempt_at timestamp(6) with time zone,
  claimed_at timestamp(6) with time zone,
  accepted_at timestamp(6) with time zone,
  bounced_at timestamp(6) with time zone,
  failed_at timestamp(6) with time zone,
  failure_code varchar(128),
  failure_reason varchar(1000),
  created_at timestamp(6) with time zone not null,
  updated_at timestamp(6) with time zone not null,
  constraint fk_broadcast_recipients_run foreign key (run_id) references broadcast_runs (id) on delete cascade,
  constraint fk_broadcast_recipients_batch foreign key (batch_id) references broadcast_batches (id) on delete cascade,
  constraint fk_broadcast_recipients_company foreign key (company_id) references companies (id) on delete cascade,
  constraint fk_broadcast_recipients_connection foreign key (connection_id) references channel_connections (id),
  constraint fk_broadcast_recipients_member foreign key (source_member_id) references broadcast_group_members (id),
  constraint uk_broadcast_recipient_member unique (run_id, source_member_id),
  constraint uk_broadcast_recipient_correlation unique (provider_correlation_id)
);

create index idx_broadcast_recipients_batch_claim
  on broadcast_recipients (batch_id, status, next_attempt_at, id);

create index idx_broadcast_recipients_message_id
  on broadcast_recipients (internet_message_id)
  where internet_message_id is not null;

create index idx_broadcast_recipients_bounce_fallback
  on broadcast_recipients (connection_id, normalized_destination, accepted_at desc)
  where status in ('ACCEPTED_BY_PROVIDER', 'UNKNOWN');

create table broadcast_delivery_attempts (
  id bigint primary key,
  recipient_id bigint not null,
  run_id bigint not null,
  attempt_no integer not null,
  status varchar(24) not null,
  provider_type varchar(40) not null,
  provider_message_id varchar(512),
  started_at timestamp(6) with time zone not null,
  completed_at timestamp(6) with time zone,
  error_code varchar(128),
  error_message varchar(1000),
  constraint fk_broadcast_attempts_recipient foreign key (recipient_id) references broadcast_recipients (id) on delete cascade,
  constraint fk_broadcast_attempts_run foreign key (run_id) references broadcast_runs (id) on delete cascade,
  constraint uk_broadcast_attempt_number unique (recipient_id, attempt_no)
);

create index idx_broadcast_attempts_run
  on broadcast_delivery_attempts (run_id, started_at desc);

create table broadcast_stats (
  id bigint primary key,
  run_id bigint not null,
  total_count bigint not null default 0,
  pending_count bigint not null default 0,
  processing_count bigint not null default 0,
  accepted_count bigint not null default 0,
  failed_count bigint not null default 0,
  bounced_count bigint not null default 0,
  unknown_count bigint not null default 0,
  skipped_count bigint not null default 0,
  updated_at timestamp(6) with time zone not null,
  constraint fk_broadcast_stats_run foreign key (run_id) references broadcast_runs (id) on delete cascade,
  constraint uk_broadcast_stats_run unique (run_id)
);

create table broadcast_suppressions (
  id bigint primary key,
  company_id bigint not null,
  channel_type varchar(24) not null,
  normalized_destination varchar(320) not null,
  reason varchar(32) not null,
  source_recipient_id bigint,
  active boolean not null default true,
  created_at timestamp(6) with time zone not null,
  updated_at timestamp(6) with time zone not null,
  constraint fk_broadcast_suppressions_company foreign key (company_id) references companies (id) on delete cascade,
  constraint fk_broadcast_suppressions_recipient foreign key (source_recipient_id) references broadcast_recipients (id) on delete set null,
  constraint uk_broadcast_suppression_destination unique (company_id, channel_type, normalized_destination)
);

create index idx_broadcast_suppressions_lookup
  on broadcast_suppressions (company_id, channel_type, active, normalized_destination);

create table broadcast_outbox (
  id bigint primary key,
  company_id bigint not null,
  aggregate_type varchar(48) not null,
  aggregate_id bigint not null,
  event_type varchar(96) not null,
  payload_json text not null,
  status varchar(24) not null,
  attempt_count integer not null default 0,
  available_at timestamp(6) with time zone not null,
  claimed_at timestamp(6) with time zone,
  published_at timestamp(6) with time zone,
  last_error varchar(1000),
  created_at timestamp(6) with time zone not null,
  updated_at timestamp(6) with time zone not null,
  constraint fk_broadcast_outbox_company foreign key (company_id) references companies (id) on delete cascade
);

create index idx_broadcast_outbox_claim
  on broadcast_outbox (status, available_at, id);

create table email_delivery_reports (
  id bigint primary key,
  company_id bigint not null,
  connection_id bigint not null,
  source_inbox_message_id bigint not null,
  failed_recipient varchar(320),
  normalized_failed_recipient varchar(320),
  original_message_id varchar(998),
  status_code varchar(32),
  diagnostic_message varchar(1000),
  bounce_type varchar(24) not null,
  detected_at timestamp(6) with time zone not null,
  processed_at timestamp(6) with time zone,
  constraint fk_email_delivery_reports_company foreign key (company_id) references companies (id) on delete cascade,
  constraint fk_email_delivery_reports_connection foreign key (connection_id) references channel_connections (id) on delete cascade,
  constraint fk_email_delivery_reports_source_message foreign key (source_inbox_message_id) references inbox_messages (id) on delete cascade,
  constraint uk_email_delivery_report_source unique (source_inbox_message_id)
);

create index idx_email_delivery_reports_unprocessed
  on email_delivery_reports (processed_at, id)
  where processed_at is null;
