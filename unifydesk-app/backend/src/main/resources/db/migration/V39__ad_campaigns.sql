create table ad_campaigns (
  id bigint primary key,
  company_id bigint not null,
  ad_account_id bigint not null,
  provider varchar(24) not null,
  external_campaign_id varchar(255) not null,
  name varchar(255) not null,
  objective varchar(80),
  buying_type varchar(40),
  bid_strategy varchar(80),
  status varchar(24) not null,
  provider_status varchar(40),
  effective_status varchar(80),
  daily_budget_minor bigint,
  lifetime_budget_minor bigint,
  spend_cap_minor bigint,
  start_at timestamp(6) with time zone,
  end_at timestamp(6) with time zone,
  provider_created_at timestamp(6) with time zone,
  provider_updated_at timestamp(6) with time zone,
  accessible boolean not null default true,
  provider_payload_json text,
  last_sync_at timestamp(6) with time zone,
  created_at timestamp(6) with time zone not null,
  updated_at timestamp(6) with time zone not null,
  constraint fk_ad_campaigns_company
    foreign key (company_id) references companies (id) on delete cascade,
  constraint fk_ad_campaigns_account
    foreign key (ad_account_id) references ad_accounts (id) on delete cascade,
  constraint uk_ad_campaign_provider_external
    unique (ad_account_id, provider, external_campaign_id)
);

create index idx_ad_campaigns_company_account
  on ad_campaigns (company_id, ad_account_id);

create index idx_ad_campaigns_account_status
  on ad_campaigns (ad_account_id, status, accessible);
