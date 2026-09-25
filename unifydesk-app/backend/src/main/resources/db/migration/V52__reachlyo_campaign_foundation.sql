create table reachlyo_campaigns (
  id bigint primary key,
  company_id bigint not null,
  name varchar(255) not null,
  objective varchar(40) not null,
  business_goal varchar(40) not null,
  landing_page_url varchar(2048),
  status varchar(32) not null,
  currency varchar(8) not null,
  start_at timestamp(6) with time zone,
  end_at timestamp(6) with time zone,
  created_by_user_id bigint not null,
  version bigint not null default 0,
  created_at timestamp(6) with time zone not null,
  updated_at timestamp(6) with time zone not null,
  constraint fk_reachlyo_campaigns_company
    foreign key (company_id) references companies (id) on delete cascade
);

create index idx_reachlyo_campaigns_company_status
  on reachlyo_campaigns (company_id, status);

create index idx_reachlyo_campaigns_company_updated
  on reachlyo_campaigns (company_id, updated_at);

create table campaign_channels (
  id bigint primary key,
  company_id bigint not null,
  reachlyo_campaign_id bigint not null,
  provider varchar(24) not null,
  channel_type varchar(48) not null,
  enabled boolean not null default true,
  budget_allocation_minor bigint,
  allocation_percent numeric(5,2),
  provider_override_json text,
  status varchar(24) not null,
  version bigint not null default 0,
  created_at timestamp(6) with time zone not null,
  updated_at timestamp(6) with time zone not null,
  constraint fk_campaign_channels_company
    foreign key (company_id) references companies (id) on delete cascade,
  constraint fk_campaign_channels_campaign
    foreign key (reachlyo_campaign_id) references reachlyo_campaigns (id) on delete cascade,
  constraint ck_campaign_channels_budget
    check (budget_allocation_minor is null or budget_allocation_minor >= 0),
  constraint ck_campaign_channels_percent
    check (allocation_percent is null or (allocation_percent >= 0 and allocation_percent <= 100)),
  constraint uk_campaign_channel_type
    unique (reachlyo_campaign_id, channel_type)
);

create index idx_campaign_channels_company_campaign
  on campaign_channels (company_id, reachlyo_campaign_id);

create index idx_campaign_channels_provider_status
  on campaign_channels (provider, status);

create table campaign_budget_plans (
  id bigint primary key,
  company_id bigint not null,
  reachlyo_campaign_id bigint not null,
  total_budget_minor bigint not null,
  budget_period varchar(16) not null,
  allocation_mode varchar(24) not null,
  version bigint not null default 0,
  created_at timestamp(6) with time zone not null,
  updated_at timestamp(6) with time zone not null,
  constraint fk_campaign_budget_plans_company
    foreign key (company_id) references companies (id) on delete cascade,
  constraint fk_campaign_budget_plans_campaign
    foreign key (reachlyo_campaign_id) references reachlyo_campaigns (id) on delete cascade,
  constraint ck_campaign_budget_plans_total
    check (total_budget_minor > 0),
  constraint uk_campaign_budget_plan_campaign
    unique (reachlyo_campaign_id)
);

create index idx_campaign_budget_plans_company_campaign
  on campaign_budget_plans (company_id, reachlyo_campaign_id);

create table provider_deployments (
  id bigint primary key,
  company_id bigint not null,
  reachlyo_campaign_id bigint not null,
  campaign_channel_id bigint not null,
  provider varchar(24) not null,
  ad_account_id bigint not null,
  local_campaign_id bigint,
  external_campaign_id varchar(255),
  provider_status varchar(80),
  deployment_status varchar(32) not null,
  last_error_code varchar(160),
  last_error_message varchar(2000),
  last_sync_at timestamp(6) with time zone,
  version bigint not null default 0,
  created_at timestamp(6) with time zone not null,
  updated_at timestamp(6) with time zone not null,
  constraint fk_provider_deployments_company
    foreign key (company_id) references companies (id) on delete cascade,
  constraint fk_provider_deployments_campaign
    foreign key (reachlyo_campaign_id) references reachlyo_campaigns (id) on delete cascade,
  constraint fk_provider_deployments_channel
    foreign key (campaign_channel_id) references campaign_channels (id) on delete cascade,
  constraint fk_provider_deployments_account
    foreign key (ad_account_id) references ad_accounts (id),
  constraint fk_provider_deployments_local_campaign
    foreign key (local_campaign_id) references ad_campaigns (id) on delete set null,
  constraint uk_provider_deployment_channel_account
    unique (campaign_channel_id, ad_account_id)
);

create index idx_provider_deployments_company_campaign
  on provider_deployments (company_id, reachlyo_campaign_id);

create index idx_provider_deployments_account_status
  on provider_deployments (ad_account_id, deployment_status);
