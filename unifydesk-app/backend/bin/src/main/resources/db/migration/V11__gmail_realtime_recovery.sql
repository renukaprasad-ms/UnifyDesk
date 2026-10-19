-- Mark provider rows seen by a complete Gmail recovery scan.
alter table inbox_messages
  add column if not exists provider_sync_marker bigint;

alter table gmail_sync_runs
  add column if not exists provider_scan_complete boolean not null default false;

create index if not exists idx_inbox_messages_gmail_recovery_marker
  on inbox_messages (connection_id, provider_sync_marker)
  where status <> 'DELETED';
