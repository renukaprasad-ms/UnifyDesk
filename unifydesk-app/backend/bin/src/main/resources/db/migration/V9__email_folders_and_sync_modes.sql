-- Normalize provider-specific folder names into provider-neutral email folders.
update inbox_messages
set provider_folder_key = 'SENT'
where upper(coalesce(provider_folder_key, '')) in ('SENT', 'SENTITEMS');

update inbox_messages
set provider_folder_key = 'INBOX'
where upper(coalesce(provider_folder_key, '')) = 'INBOX';

-- Legacy Gmail rows predate provider_folder_key. Direction is the best available
-- fallback for those rows; all newly synced Gmail messages persist exact labels.
update inbox_messages
set provider_folder_key =
  case when direction = 'OUTBOUND' then 'SENT' else 'INBOX' end
where provider_folder_key is null;

create index if not exists idx_inbox_messages_connection_folder_sent
  on inbox_messages (connection_id, provider_folder_key, sent_at desc);

-- Gmail historical sync now supports the same provider-neutral request shape as Microsoft.
alter table gmail_sync_runs
  alter column from_date drop not null,
  alter column to_date drop not null,
  add column if not exists sync_mode varchar(32) not null default 'DATE_RANGE',
  add column if not exists folder_key varchar(32) not null default 'INBOX',
  add column if not exists message_count integer;

-- Durable Microsoft historical backfills are intentionally separate from live delta cursors.
create table if not exists microsoft_backfill_runs (
  job_id bigint primary key,
  company_id bigint not null,
  user_id bigint not null,
  connection_id bigint not null,
  folder_key varchar(32) not null,
  sync_mode varchar(32) not null,
  from_date date,
  to_date date,
  message_limit integer,
  processed_count bigint not null default 0,
  next_url text,
  status varchar(24) not null,
  attempt_count integer not null default 0,
  next_attempt_at timestamp(6) with time zone,
  claimed_at timestamp(6) with time zone,
  last_error varchar(1000),
  created_at timestamp(6) with time zone not null,
  updated_at timestamp(6) with time zone not null,
  constraint fk_microsoft_backfill_job foreign key (job_id) references async_jobs (id),
  constraint fk_microsoft_backfill_company foreign key (company_id) references companies (id),
  constraint fk_microsoft_backfill_connection foreign key (connection_id) references channel_connections (id)
);

create index if not exists idx_microsoft_backfill_claim
  on microsoft_backfill_runs (status, next_attempt_at, connection_id, updated_at);
