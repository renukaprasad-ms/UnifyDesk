-- V118__crm_lead_forms.sql
-- Web-to-Lead Capture Forms for UnifyDesk CRM

create table if not exists crm_lead_forms (
  id bigint primary key,
  company_id bigint not null references companies(id) on delete cascade,
  form_key varchar(64) not null unique,
  title varchar(140) not null check (btrim(title) <> ''),
  description varchar(500),
  status varchar(20) not null default 'ACTIVE' check (status in ('ACTIVE', 'PAUSED')),
  default_lead_stage varchar(20) not null default 'NEW',
  assignee_user_id bigint references users(id) on delete set null,
  success_message varchar(500) not null default 'Thank you! We will get in touch with you shortly.',
  redirect_url varchar(1000),
  fields_config jsonb not null default '[]'::jsonb,
  submissions_count bigint not null default 0 check (submissions_count >= 0),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (company_id, id)
);

create index if not exists idx_crm_lead_forms_company on crm_lead_forms(company_id, created_at desc);

create table if not exists crm_form_submissions (
  id bigint primary key,
  company_id bigint not null references companies(id) on delete cascade,
  form_id bigint not null,
  contact_id bigint,
  payload jsonb not null default '{}'::jsonb,
  ip_address varchar(64),
  user_agent varchar(500),
  submitted_at timestamptz not null default now(),
  foreign key (company_id, form_id) references crm_lead_forms(company_id, id) on delete cascade,
  foreign key (company_id, contact_id) references contacts(company_id, id) on delete set null
);

create index if not exists idx_crm_form_submissions_form on crm_form_submissions(company_id, form_id, submitted_at desc);
