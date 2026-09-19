-- Gmail message addressing/thread headers used for compose, reply and reply-all.
alter table inbox_messages
  add column if not exists from_address varchar(320),
  add column if not exists reply_to_address varchar(320),
  add column if not exists to_addresses text,
  add column if not exists cc_addresses text,
  add column if not exists bcc_addresses text,
  add column if not exists in_reply_to varchar(998),
  add column if not exists references_header text;

-- Durable, resumable Gmail batch synchronization.

alter table async_jobs
  add column if not exists resource_id varchar(160);

create index if not exists idx_async_jobs_company_type_resource_status
  on async_jobs (company_id, job_type, resource_id, status, created_at desc);

create unique index if not exists uk_async_jobs_active_resource
  on async_jobs (company_id, job_type, resource_id)
  where resource_id is not null and status in ('QUEUED', 'RUNNING');

create table if not exists gmail_sync_runs (
  job_id bigint primary key,
  company_id bigint not null,
  user_id bigint not null,
  connection_id bigint not null,
  from_date date not null,
  to_date date not null,
  discovery_complete boolean not null default false,
  finalized boolean not null default false,
  discovered_count bigint not null default 0,
  existing_count bigint not null default 0,
  batch_count integer not null default 0,
  failed_count bigint not null default 0,
  next_batch_index integer not null default 0,
  next_page_token text,
  carry_ids_json text not null default '[]',
  history_id varchar(128),
  created_at timestamp(6) with time zone not null,
  updated_at timestamp(6) with time zone not null,
  constraint fk_gmail_sync_runs_job foreign key (job_id) references async_jobs (id),
  constraint fk_gmail_sync_runs_company foreign key (company_id) references companies (id),
  constraint fk_gmail_sync_runs_connection foreign key (connection_id) references channel_connections (id)
);

create index if not exists idx_gmail_sync_runs_connection
  on gmail_sync_runs (company_id, connection_id, created_at desc);

create table if not exists gmail_sync_batches (
  id bigint primary key,
  job_id bigint not null,
  company_id bigint not null,
  connection_id bigint not null,
  batch_index integer not null,
  status varchar(24) not null,
  message_ids_json text not null,
  message_count integer not null,
  processed_count integer not null default 0,
  failed_count integer not null default 0,
  attempt_count integer not null default 0,
  last_error varchar(1000),
  created_at timestamp(6) with time zone not null,
  updated_at timestamp(6) with time zone not null,
  completed_at timestamp(6) with time zone,
  constraint fk_gmail_sync_batches_job foreign key (job_id) references async_jobs (id),
  constraint fk_gmail_sync_batches_company foreign key (company_id) references companies (id),
  constraint fk_gmail_sync_batches_connection foreign key (connection_id) references channel_connections (id),
  constraint uk_gmail_sync_batches_job_index unique (job_id, batch_index)
);

create index if not exists idx_gmail_sync_batches_job_status
  on gmail_sync_batches (job_id, status, batch_index);

create index if not exists idx_gmail_sync_batches_recovery
  on gmail_sync_batches (status, updated_at);
