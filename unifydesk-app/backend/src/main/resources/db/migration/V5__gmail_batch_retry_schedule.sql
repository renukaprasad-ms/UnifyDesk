-- Durable Gmail provider backoff. A throttled mailbox remains retryable without
-- immediately re-claiming the same batch or hammering subsequent batches.
alter table gmail_sync_batches
  add column if not exists next_attempt_at timestamp(6) with time zone;

create index if not exists idx_gmail_sync_batches_retry_schedule
  on gmail_sync_batches (status, next_attempt_at, connection_id, batch_index);
