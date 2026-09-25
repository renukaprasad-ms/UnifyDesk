-- Durable Gmail push-watch and incremental history processing state.
alter table channel_sync_states
  add column if not exists watch_expiration_at timestamp(6) with time zone,
  add column if not exists watch_renewed_at timestamp(6) with time zone,
  add column if not exists last_notification_at timestamp(6) with time zone,
  add column if not exists pending_history_id varchar(255),
  add column if not exists realtime_claimed_at timestamp(6) with time zone,
  add column if not exists realtime_next_attempt_at timestamp(6) with time zone,
  add column if not exists realtime_attempt_count integer not null default 0,
  add column if not exists realtime_last_error varchar(1000);

create index if not exists idx_channel_sync_states_gmail_realtime_claim
  on channel_sync_states (provider_type, realtime_next_attempt_at, updated_at)
  where provider_type = 'GMAIL' and pending_history_id is not null;

create index if not exists idx_channel_sync_states_gmail_watch_expiration
  on channel_sync_states (provider_type, watch_expiration_at)
  where provider_type = 'GMAIL';
