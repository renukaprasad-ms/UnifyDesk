-- CRM Lite R1: a lead is an existing Contact, not a second person record.
-- A company-scoped FK prevents linking another workspace's contact.
create table crm_leads (
  company_id bigint not null,
  contact_id bigint not null,
  stage varchar(20) not null default 'NEW',
  version bigint not null default 0,
  created_by_user_id bigint,
  updated_by_user_id bigint,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key (company_id, contact_id),
  constraint fk_crm_leads_contact foreign key (company_id, contact_id)
    references contacts(company_id, id) on delete cascade,
  constraint fk_crm_leads_creator foreign key (created_by_user_id)
    references users(id) on delete set null,
  constraint fk_crm_leads_updater foreign key (updated_by_user_id)
    references users(id) on delete set null,
  constraint ck_crm_leads_stage
    check (stage in ('NEW', 'CONTACTED', 'QUALIFIED', 'UNQUALIFIED', 'CONVERTED')),
  constraint ck_crm_leads_version check (version >= 0)
);

create index idx_crm_leads_stage_page
  on crm_leads(company_id, stage, contact_id desc);

create table crm_lead_stage_events (
  id bigint primary key,
  company_id bigint not null,
  contact_id bigint not null,
  previous_stage varchar(20),
  new_stage varchar(20) not null,
  actor_user_id bigint,
  occurred_at timestamptz not null default now(),
  constraint fk_crm_lead_stage_events_lead
    foreign key (company_id, contact_id)
    references crm_leads(company_id, contact_id) on delete cascade,
  constraint fk_crm_lead_stage_events_actor
    foreign key (actor_user_id) references users(id) on delete set null,
  constraint ck_crm_lead_stage_events_new
    check (new_stage in ('NEW', 'CONTACTED', 'QUALIFIED', 'UNQUALIFIED', 'CONVERTED')),
  constraint ck_crm_lead_stage_events_old
    check (previous_stage is null or previous_stage in
      ('NEW', 'CONTACTED', 'QUALIFIED', 'UNQUALIFIED', 'CONVERTED'))
);

create index idx_crm_lead_stage_events_history
  on crm_lead_stage_events(company_id, contact_id, occurred_at desc, id desc);
