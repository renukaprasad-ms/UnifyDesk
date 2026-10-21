-- Billing V2 Mail bridge cursors always query one project ordered by immutable usage ID.
-- The existing (project_id, occurred_at) index supports time window summaries but
-- does not efficiently serve project-scoped ID cursor paging.
create index if not exists idx_email_product_usage_project_id_cursor
  on email_product_usage_entries (project_id, id);

-- Bounded lookback replay filters by both project and handoff time.
-- Existing idx_email_product_usage_project_date is retained.
