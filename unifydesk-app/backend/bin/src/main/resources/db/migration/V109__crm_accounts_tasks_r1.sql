-- CRM Lite R1: customer business accounts and sales follow-up tasks.
-- "crm_accounts" are customer organizations, never UnifyDesk tenants.
create table crm_accounts (
  id bigint primary key,
  company_id bigint not null references companies(id) on delete restrict,
  name varchar(240) not null,
  normalized_name varchar(240) not null,
  website varchar(320),
  notes varchar(4000),
  owner_user_id bigint references users(id) on delete set null,
  version bigint not null default 0 check (version >= 0),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint ck_crm_accounts_name check (btrim(name) <> ''),
  constraint uk_crm_accounts_company_id unique (company_id, id)
);
create unique index uk_crm_accounts_active_name on crm_accounts(company_id, normalized_name);
create index idx_crm_accounts_page on crm_accounts(company_id, id desc);

create table crm_account_contacts (
  company_id bigint not null,
  account_id bigint not null,
  contact_id bigint not null,
  linked_at timestamptz not null default now(),
  linked_by_user_id bigint references users(id) on delete set null,
  primary key (company_id, account_id, contact_id),
  constraint fk_crm_account_links_account foreign key (company_id, account_id)
    references crm_accounts(company_id, id) on delete cascade,
  constraint fk_crm_account_links_contact foreign key (company_id, contact_id)
    references contacts(company_id, id) on delete cascade
);
create index idx_crm_account_contacts_by_contact on crm_account_contacts(company_id, contact_id, account_id);

create table crm_tasks (
  id bigint primary key,
  company_id bigint not null references companies(id) on delete restrict,
  title varchar(240) not null,
  details varchar(4000),
  status varchar(20) not null default 'OPEN',
  priority varchar(12) not null default 'NORMAL',
  due_at timestamptz,
  assignee_user_id bigint not null references users(id) on delete restrict,
  created_by_user_id bigint references users(id) on delete set null,
  contact_id bigint,
  account_id bigint,
  version bigint not null default 0 check (version >= 0),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  completed_at timestamptz,
  constraint uk_crm_tasks_company_id unique (company_id, id),
  constraint fk_crm_tasks_contact foreign key (company_id, contact_id)
    references contacts(company_id, id) on delete set null (contact_id),
  constraint fk_crm_tasks_account foreign key (company_id, account_id)
    references crm_accounts(company_id, id) on delete set null (account_id),
  constraint ck_crm_tasks_title check (btrim(title) <> ''),
  constraint ck_crm_tasks_status check (status in ('OPEN','IN_PROGRESS','DONE','CANCELLED')),
  constraint ck_crm_tasks_priority check (priority in ('LOW','NORMAL','HIGH')),
  constraint ck_crm_tasks_completion check (
    (status = 'DONE' and completed_at is not null)
    or (status <> 'DONE' and completed_at is null)
  )
);
create index idx_crm_tasks_assignee_due on crm_tasks(company_id, assignee_user_id, status, due_at, id);
create index idx_crm_tasks_company_page on crm_tasks(company_id, id desc);
create index idx_crm_tasks_contact on crm_tasks(company_id, contact_id, due_at) where contact_id is not null;
create index idx_crm_tasks_account on crm_tasks(company_id, account_id, due_at) where account_id is not null;
