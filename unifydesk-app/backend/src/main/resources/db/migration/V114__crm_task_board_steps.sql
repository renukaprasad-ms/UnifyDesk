create table crm_task_columns (
  company_id bigint not null references companies(id) on delete restrict,
  column_key varchar(40) not null,
  name varchar(80) not null check (btrim(name) <> ''),
  status_group varchar(20) not null check (status_group in ('OPEN','IN_PROGRESS','DONE','CANCELLED')),
  position integer not null check (position >= 0),
  system_column boolean not null default false,
  version bigint not null default 0 check (version >= 0),
  primary key(company_id,column_key)
);
create unique index uk_crm_task_columns_name on crm_task_columns(company_id,lower(name));
insert into crm_task_columns(company_id,column_key,name,status_group,position,system_column)
select c.id,d.key,d.name,d.key,d.position,true from companies c cross join
  (values ('OPEN','To do',0),('IN_PROGRESS','In progress',1),('DONE','Done',2),('CANCELLED','Cancelled',3)) d(key,name,position);

alter table crm_tasks add column column_key varchar(40);
alter table crm_tasks add constraint fk_crm_tasks_column foreign key(company_id,column_key)
  references crm_task_columns(company_id,column_key) on delete restrict;
update crm_tasks set column_key=status;
create index idx_crm_tasks_column on crm_tasks(company_id,column_key,id desc);

alter table crm_task_events add column previous_column varchar(80), add column new_column varchar(80);
alter table crm_task_events drop constraint crm_task_events_event_type_check;
alter table crm_task_events add constraint crm_task_events_event_type_check
  check(event_type in ('CREATED','UPDATED','STATUS_CHANGED','STEP_CHANGED'));
