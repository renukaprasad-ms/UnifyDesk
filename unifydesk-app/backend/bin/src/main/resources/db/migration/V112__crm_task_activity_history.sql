create table crm_task_events (
  id bigint primary key,
  company_id bigint not null,
  task_id bigint not null,
  event_type varchar(24) not null check (event_type in ('CREATED','UPDATED','STATUS_CHANGED')),
  previous_status varchar(20),
  new_status varchar(20) not null,
  changed_fields varchar(240),
  actor_user_id bigint references users(id) on delete set null,
  occurred_at timestamptz not null default now(),
  constraint fk_crm_task_events_task foreign key (company_id, task_id)
    references crm_tasks(company_id, id) on delete cascade
);
create index idx_crm_task_events_history
  on crm_task_events(company_id, task_id, occurred_at desc, id desc);
