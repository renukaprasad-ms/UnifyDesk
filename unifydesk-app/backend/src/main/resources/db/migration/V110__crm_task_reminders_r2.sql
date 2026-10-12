-- CRM R2: generation-based reminder deduplication. Delivery goes through the
-- existing notification event outbox; no external provider calls occur in the poller.
alter table crm_tasks
  add column reminder_generation bigint not null default 0,
  add column reminded_generation bigint;

alter table crm_tasks
  add constraint ck_crm_tasks_reminder_generation
  check (reminder_generation >= 0 and
         (reminded_generation is null or reminded_generation >= 0));

create index idx_crm_tasks_due_reminder_scan
  on crm_tasks(due_at, id)
  where due_at is not null and status in ('OPEN', 'IN_PROGRESS');
