CREATE TABLE email_product_batches (
  id BIGINT PRIMARY KEY,
  project_id BIGINT NOT NULL REFERENCES email_product_projects(id),
  idempotency_key VARCHAR(128) NOT NULL,
  request_hash VARCHAR(64) NOT NULL,
  total_recipients INTEGER NOT NULL,
  chunks_count INTEGER NOT NULL,
  suppressed_recipients INTEGER NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL,
  CONSTRAINT uq_email_product_batch_idempotency UNIQUE(project_id, idempotency_key)
);
CREATE INDEX idx_email_product_batch_project ON email_product_batches(project_id, created_at DESC);

CREATE TABLE email_product_batch_chunks (
  id BIGINT PRIMARY KEY,
  batch_id BIGINT NOT NULL REFERENCES email_product_batches(id),
  chunk_number INTEGER NOT NULL,
  message_id BIGINT NOT NULL REFERENCES email_messages(id),
  recipient_count INTEGER NOT NULL,
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL,
  CONSTRAINT uq_email_product_batch_chunk UNIQUE(batch_id, chunk_number)
);
CREATE INDEX idx_email_product_batch_chunks ON email_product_batch_chunks(batch_id, chunk_number);
