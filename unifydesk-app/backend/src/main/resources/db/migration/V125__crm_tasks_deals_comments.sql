-- V125__crm_tasks_deals_comments.sql
-- Unified Comments and Task Type Categorization for Tasks & Deals

alter table crm_tasks
  add column if not exists task_type varchar(30) not null default 'TODO';

create table if not exists crm_comments (
  id bigint primary key,
  company_id bigint not null references companies(id) on delete cascade,
  entity_type varchar(30) not null check (entity_type in ('TASK', 'DEAL', 'LEAD')),
  entity_id bigint not null,
  body text not null check (btrim(body) <> ''),
  author_user_id bigint references users(id) on delete set null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create index if not exists idx_crm_comments_entity
  on crm_comments (company_id, entity_type, entity_id, created_at desc);

create index if not exists idx_crm_tasks_type
  on crm_tasks (company_id, task_type);
