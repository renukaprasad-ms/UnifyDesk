alter table channel_connections
  add column if not exists disconnected_at timestamp(6) with time zone,
  add column if not exists purge_after timestamp(6) with time zone,
  add column if not exists deletion_requested_by bigint;

create index if not exists idx_channel_connections_disconnected_purge
  on channel_connections (status, purge_after)
  where status = 'DISCONNECTED';
