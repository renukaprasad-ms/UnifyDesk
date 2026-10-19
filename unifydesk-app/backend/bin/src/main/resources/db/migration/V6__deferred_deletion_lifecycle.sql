-- Deferred deletion lifecycle.
-- User/company records remain recoverable during the retention window. Operational
-- data is purged later while financial/audit references can keep an anonymized tombstone.

alter table companies add column if not exists purge_after timestamp(6) with time zone;
alter table companies add column if not exists deletion_requested_by bigint;
alter table companies add column if not exists purge_started_at timestamp(6) with time zone;
alter table companies add column if not exists purged_at timestamp(6) with time zone;

alter table users add column if not exists purge_after timestamp(6) with time zone;
alter table users add column if not exists deletion_requested_by bigint;
alter table users add column if not exists purge_started_at timestamp(6) with time zone;
alter table users add column if not exists purged_at timestamp(6) with time zone;

-- Preserve compatibility with the previous immediate-delete lifecycle.
update companies
set status = 'DELETION_PENDING',
    purge_after = coalesce(purge_after, coalesce(deleted_at, now()) + interval '30 days')
where status = 'DELETED';

update users
set status = 'DELETION_PENDING',
    purge_after = coalesce(purge_after, coalesce(deleted_at, now()) + interval '30 days')
where status = 'DELETED';

create index if not exists idx_companies_purge_due
  on companies (status, purge_after);

create index if not exists idx_users_purge_due
  on users (status, purge_after);

create table if not exists deletion_purge_jobs (
  id bigint primary key,
  entity_type varchar(40) not null,
  entity_id bigint not null,
  company_id bigint,
  requested_by_user_id bigint,
  status varchar(24) not null,
  purge_after timestamp(6) with time zone not null,
  next_attempt_at timestamp(6) with time zone,
  attempt_count integer not null default 0,
  last_error varchar(1000),
  claimed_at timestamp(6) with time zone,
  completed_at timestamp(6) with time zone,
  created_at timestamp(6) with time zone not null,
  updated_at timestamp(6) with time zone not null,
  constraint uk_deletion_purge_jobs_entity unique (entity_type, entity_id)
);

create index if not exists idx_deletion_purge_jobs_due
  on deletion_purge_jobs (status, purge_after, next_attempt_at);

-- Backfill durable purge work for entities that were already soft-deleted before
-- this migration. Negative IDs are reserved for migration-only rows so they cannot
-- collide with the application's positive Snowflake IDs.
insert into deletion_purge_jobs (
  id, entity_type, entity_id, company_id, requested_by_user_id, status,
  purge_after, attempt_count, created_at, updated_at
)
select
  -row_number() over (order by id),
  'COMPANY',
  id,
  id,
  deletion_requested_by,
  'PENDING',
  purge_after,
  0,
  now(),
  now()
from companies
where status = 'DELETION_PENDING'
on conflict (entity_type, entity_id) do nothing;


insert into deletion_purge_jobs (
  id, entity_type, entity_id, company_id, requested_by_user_id, status,
  purge_after, attempt_count, created_at, updated_at
)
select
  -1000000000 - row_number() over (order by id),
  'USER',
  id,
  null,
  coalesce(deletion_requested_by, id),
  'PENDING',
  purge_after,
  0,
  now(),
  now()
from users
where status = 'DELETION_PENDING'
on conflict (entity_type, entity_id) do nothing;
