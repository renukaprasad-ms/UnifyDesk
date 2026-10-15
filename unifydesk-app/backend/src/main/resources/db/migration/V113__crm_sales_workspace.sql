create table crm_deals (
  id bigint primary key,
  company_id bigint not null references companies(id) on delete restrict,
  title varchar(240) not null check (btrim(title) <> ''),
  description varchar(4000),
  stage varchar(20) not null default 'NEW' check (stage in ('NEW','QUALIFIED','PROPOSAL','NEGOTIATION','WON','LOST')),
  amount numeric(20,3) not null default 0 check (amount >= 0),
  currency varchar(3) not null,
  expected_close_date date,
  owner_user_id bigint references users(id) on delete set null,
  contact_id bigint,
  account_id bigint,
  created_by_user_id bigint references users(id) on delete set null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  closed_at timestamptz,
  version bigint not null default 0 check (version >= 0),
  unique (company_id,id),
  foreign key (company_id,contact_id) references contacts(company_id,id) on delete set null (contact_id),
  foreign key (company_id,account_id) references crm_accounts(company_id,id) on delete set null (account_id),
  check ((stage in ('WON','LOST')) = (closed_at is not null))
);
create index idx_crm_deals_page on crm_deals(company_id,id desc);
create index idx_crm_deals_stage on crm_deals(company_id,stage,id desc);
create index idx_crm_deals_owner on crm_deals(company_id,owner_user_id,expected_close_date);
create index idx_crm_deals_contact on crm_deals(company_id,contact_id) where contact_id is not null;

create table crm_deal_events (
  id bigint primary key,
  company_id bigint not null,
  deal_id bigint not null,
  event_type varchar(24) not null check (event_type in ('CREATED','UPDATED','STAGE_CHANGED')),
  previous_stage varchar(20),
  new_stage varchar(20) not null,
  changed_fields varchar(240),
  actor_user_id bigint references users(id) on delete set null,
  occurred_at timestamptz not null default now(),
  foreign key (company_id,deal_id) references crm_deals(company_id,id) on delete cascade
);
create index idx_crm_deal_events_history on crm_deal_events(company_id,deal_id,occurred_at desc,id desc);
create index idx_crm_deal_events_feed on crm_deal_events(company_id,occurred_at desc,id desc);
create index idx_crm_task_events_feed on crm_task_events(company_id,occurred_at desc,id desc);
create index idx_crm_lead_events_feed on crm_lead_stage_events(company_id,occurred_at desc,id desc);

create table crm_notes (
  id bigint primary key,
  company_id bigint not null references companies(id) on delete restrict,
  contact_id bigint,
  body varchar(2000) not null check (btrim(body) <> ''),
  actor_user_id bigint references users(id) on delete set null,
  occurred_at timestamptz not null default now(),
  foreign key (company_id,contact_id) references contacts(company_id,id) on delete cascade
);
create index idx_crm_notes_feed on crm_notes(company_id,occurred_at desc,id desc);
