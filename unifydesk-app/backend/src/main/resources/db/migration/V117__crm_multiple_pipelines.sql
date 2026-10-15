-- V117__crm_multiple_pipelines.sql
-- Multiple Sales Pipelines & Custom Stages for UnifyDesk CRM

create table if not exists crm_pipelines (
  id bigint primary key,
  company_id bigint not null references companies(id) on delete cascade,
  name varchar(120) not null check (btrim(name) <> ''),
  description varchar(500),
  is_default boolean not null default false,
  display_order int not null default 0,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (company_id, id)
);

create index if not exists idx_crm_pipelines_company on crm_pipelines(company_id, display_order, id);
create unique index if not exists idx_crm_pipelines_default on crm_pipelines(company_id) where is_default = true;

create table if not exists crm_pipeline_stages (
  id bigint primary key,
  company_id bigint not null references companies(id) on delete cascade,
  pipeline_id bigint not null,
  key varchar(50) not null check (btrim(key) <> ''),
  name varchar(100) not null check (btrim(name) <> ''),
  category varchar(20) not null default 'OPEN' check (category in ('OPEN', 'WON', 'LOST')),
  probability int not null default 0 check (probability >= 0 and probability <= 100),
  display_order int not null default 0,
  color varchar(30) not null default 'indigo',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (company_id, id),
  unique (company_id, pipeline_id, key),
  foreign key (company_id, pipeline_id) references crm_pipelines(company_id, id) on delete cascade
);

create index if not exists idx_crm_pipeline_stages_order on crm_pipeline_stages(company_id, pipeline_id, display_order, id);

-- Relax existing deal stage constraints
alter table crm_deals drop constraint if exists crm_deals_stage_check;
alter table crm_deals drop constraint if exists crm_deals_check;

-- Add pipeline_id to crm_deals
alter table crm_deals add column if not exists pipeline_id bigint;

-- Backfill existing companies with a default pipeline & stages
do $$
declare
  c record;
  p_id bigint;
  base_stage_id bigint;
begin
  for c in select id from companies loop
    if not exists (select 1 from crm_pipelines where company_id = c.id) then
      p_id := (abs(hashtext(c.id::text || 'pipeline_salt'))::bigint * 1000) + 1;
      
      insert into crm_pipelines (id, company_id, name, description, is_default, display_order)
      values (p_id, c.id, 'Standard Sales Pipeline', 'Default sales pipeline', true, 0)
      on conflict (company_id, id) do nothing;

      base_stage_id := (abs(hashtext(c.id::text || 'stage_salt'))::bigint * 1000);

      insert into crm_pipeline_stages (id, company_id, pipeline_id, key, name, category, probability, display_order, color)
      values
        (base_stage_id + 1, c.id, p_id, 'NEW', 'New', 'OPEN', 10, 1, 'sky'),
        (base_stage_id + 2, c.id, p_id, 'QUALIFIED', 'Qualified', 'OPEN', 25, 2, 'indigo'),
        (base_stage_id + 3, c.id, p_id, 'PROPOSAL', 'Proposal', 'OPEN', 50, 3, 'violet'),
        (base_stage_id + 4, c.id, p_id, 'NEGOTIATION', 'Negotiation', 'OPEN', 75, 4, 'amber'),
        (base_stage_id + 5, c.id, p_id, 'WON', 'Won', 'WON', 100, 5, 'emerald'),
        (base_stage_id + 6, c.id, p_id, 'LOST', 'Lost', 'LOST', 0, 6, 'rose')
      on conflict do nothing;

      update crm_deals set pipeline_id = p_id where company_id = c.id and pipeline_id is null;
    else
      select id into p_id from crm_pipelines where company_id = c.id order by is_default desc, id asc limit 1;
      update crm_deals set pipeline_id = p_id where company_id = c.id and pipeline_id is null;
    end if;
  end loop;
end $$;

-- Add foreign key constraint and index
do $$
begin
  if not exists (select 1 from pg_constraint where conname = 'fk_crm_deals_pipeline') then
    alter table crm_deals add constraint fk_crm_deals_pipeline
      foreign key (company_id, pipeline_id) references crm_pipelines(company_id, id) on delete restrict;
  end if;
end $$;

create index if not exists idx_crm_deals_pipeline on crm_deals(company_id, pipeline_id, stage, id desc);
