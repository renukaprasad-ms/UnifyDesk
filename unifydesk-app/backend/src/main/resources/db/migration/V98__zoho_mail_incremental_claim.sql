create index if not exists idx_channel_sync_states_zoho_incremental_claim
  on channel_sync_states (provider_type, incremental_next_attempt_at, updated_at)
  where provider_type = 'ZOHO_MAIL';
