-- V130__crm_task_timer_and_scoped_access.sql
-- 1. Track when a task entered IN_PROGRESS to automatically calculate work session elapsed time
alter table crm_tasks
  add column if not exists in_progress_started_at timestamptz;

-- 2. Performance index for owner-based scoped contact access
create index if not exists idx_contacts_company_owner
  on contacts(company_id, owner_user_id) where owner_user_id is not null;
