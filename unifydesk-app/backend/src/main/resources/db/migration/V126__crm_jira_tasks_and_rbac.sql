-- V126__crm_jira_tasks_and_rbac.sql
-- Jira-grade task extensions: Subtasks, Attachments, Estimates, Deal Linking, WIP limits & Granular CRM RBAC permissions

-- 1. Extend crm_tasks with Deal linking and time tracking
alter table crm_tasks
  add column if not exists deal_id bigint,
  add column if not exists estimated_minutes integer check (estimated_minutes is null or estimated_minutes >= 0),
  add column if not exists logged_minutes integer not null default 0 check (logged_minutes >= 0);

do $$
begin
  if not exists (
    select 1 from pg_constraint where conname = 'fk_crm_tasks_deal'
  ) then
    alter table crm_tasks
      add constraint fk_crm_tasks_deal foreign key (company_id, deal_id)
        references crm_deals(company_id, id) on delete set null (deal_id);
  end if;
end $$;

create index if not exists idx_crm_tasks_deal
  on crm_tasks(company_id, deal_id) where deal_id is not null;

-- 2. Extend crm_task_columns with WIP limits
alter table crm_task_columns
  add column if not exists wip_limit integer check (wip_limit is null or wip_limit > 0);

-- 3. Subtasks / Checklist items
create table if not exists crm_subtasks (
  id bigint primary key,
  company_id bigint not null references companies(id) on delete cascade,
  task_id bigint not null,
  title varchar(300) not null check (btrim(title) <> ''),
  completed boolean not null default false,
  position integer not null default 0 check (position >= 0),
  completed_by_user_id bigint references users(id) on delete set null,
  completed_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint fk_crm_subtasks_task foreign key (company_id, task_id)
    references crm_tasks(company_id, id) on delete cascade
);

create index if not exists idx_crm_subtasks_task
  on crm_subtasks(company_id, task_id, position asc);

-- 4. Task file & document attachments
create table if not exists crm_task_attachments (
  id bigint primary key,
  company_id bigint not null references companies(id) on delete cascade,
  task_id bigint not null,
  media_id bigint not null references media(id) on delete restrict,
  filename varchar(255) not null,
  content_type varchar(100),
  size_bytes bigint not null,
  uploader_user_id bigint references users(id) on delete set null,
  created_at timestamptz not null default now(),
  constraint fk_crm_task_attachments_task foreign key (company_id, task_id)
    references crm_tasks(company_id, id) on delete cascade
);

create index if not exists idx_crm_task_attachments_task
  on crm_task_attachments(company_id, task_id, created_at desc);

-- 5. Insert granular CRM permissions into the catalog
insert into permissions (permission_key, resource, action, description, assignable) values
  ('crm.task.read', 'crm.task', 'read', 'View tasks and board', true),
  ('crm.task.create', 'crm.task', 'create', 'Create new tasks and subtasks', true),
  ('crm.task.edit', 'crm.task', 'edit', 'Edit task details, priority, and due dates', true),
  ('crm.task.status_change', 'crm.task', 'status_change', 'Move tasks across board columns', true),
  ('crm.task.assign', 'crm.task', 'assign', 'Assign tasks to team members', true),
  ('crm.task.delete', 'crm.task', 'delete', 'Delete tasks', true),
  ('crm.task.comment', 'crm.task', 'comment', 'Add comments and discussions to tasks', true),
  ('crm.task.workflow.modify', 'crm.task.workflow', 'modify', 'Configure board columns and WIP limits', true),

  ('crm.deal.read', 'crm.deal', 'read', 'View deals and pipelines', true),
  ('crm.deal.create', 'crm.deal', 'create', 'Create new sales deals', true),
  ('crm.deal.edit', 'crm.deal', 'edit', 'Edit deal amounts, dates, and attributes', true),
  ('crm.deal.stage_change', 'crm.deal', 'stage_change', 'Move deals across pipeline stages', true),
  ('crm.deal.assign', 'crm.deal', 'assign', 'Assign deals to sales reps', true),
  ('crm.deal.delete', 'crm.deal', 'delete', 'Delete deals', true),
  ('crm.deal.pipeline.modify', 'crm.deal.pipeline', 'modify', 'Create and configure sales pipelines and stages', true),

  ('crm.lead.read', 'crm.lead', 'read', 'View sales leads', true),
  ('crm.lead.create', 'crm.lead', 'create', 'Enroll new leads', true),
  ('crm.lead.edit', 'crm.lead', 'edit', 'Edit lead attributes and custom fields', true),
  ('crm.lead.stage_change', 'crm.lead', 'stage_change', 'Move leads through qualification stages', true),
  ('crm.lead.assign', 'crm.lead', 'assign', 'Assign leads to sales reps', true),
  ('crm.lead.delete', 'crm.lead', 'delete', 'Delete leads', true),

  ('crm.account.read', 'crm.account', 'read', 'View customer company accounts', true),
  ('crm.account.create', 'crm.account', 'create', 'Create new customer company accounts', true),
  ('crm.account.edit', 'crm.account', 'edit', 'Edit customer company accounts', true),
  ('crm.account.delete', 'crm.account', 'delete', 'Delete customer company accounts', true)
on conflict (permission_key) do update set
  resource = excluded.resource,
  action = excluded.action,
  description = excluded.description,
  assignable = excluded.assignable;
