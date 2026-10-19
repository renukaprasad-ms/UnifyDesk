alter table channel_sync_states
  add column if not exists incremental_claimed_at timestamp(6) with time zone,
  add column if not exists incremental_next_attempt_at timestamp(6) with time zone,
  add column if not exists incremental_attempt_count integer not null default 0,
  add column if not exists incremental_last_error varchar(1000);

create index if not exists idx_channel_sync_states_imap_incremental_claim
  on channel_sync_states (provider_type, incremental_next_attempt_at, updated_at)
  where provider_type = 'IMAP_SMTP';
