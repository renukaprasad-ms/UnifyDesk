-- V122__crm_lead_scoring.sql
-- Rule-Based Lead Scoring System for UnifyDesk CRM

alter table crm_leads
  add column if not exists score int not null default 0,
  add column if not exists temperature varchar(10) not null default 'COLD',
  add column if not exists score_breakdown jsonb not null default '[]'::jsonb,
  add column if not exists scored_at timestamptz;

create index if not exists idx_crm_leads_score
  on crm_leads (company_id, score desc);

create index if not exists idx_crm_leads_temp
  on crm_leads (company_id, temperature);

create table if not exists crm_lead_score_settings (
  company_id bigint primary key references companies(id) on delete cascade,
  hot_threshold int not null default 70 check (hot_threshold > warm_threshold),
  warm_threshold int not null default 30 check (warm_threshold >= 0),
  updated_at timestamptz not null default now()
);

create table if not exists crm_lead_score_rules (
  id bigint primary key,
  company_id bigint not null references companies(id) on delete cascade,
  rule_name varchar(100) not null check (btrim(rule_name) <> ''),
  description varchar(255),
  rule_type varchar(50) not null,
  condition_config jsonb not null default '{}'::jsonb,
  points int not null,
  is_enabled boolean not null default true,
  display_order int not null default 0,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (company_id, id)
);

create index if not exists idx_crm_lead_score_rules_company
  on crm_lead_score_rules (company_id, display_order, created_at);
