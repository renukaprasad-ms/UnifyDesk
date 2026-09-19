-- Supports fair, low-latency DB-backed Gmail batch claiming.
create index if not exists idx_gmail_sync_batches_worker_claim
  on gmail_sync_batches (status, connection_id, batch_index, updated_at, created_at);
