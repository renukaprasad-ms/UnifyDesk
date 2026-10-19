create table ad_sets (
  id bigint primary key,
  company_id bigint not null,
  ad_account_id bigint not null,
  campaign_id bigint not null,
  provider varchar(24) not null,
  external_ad_set_id varchar(255) not null,
  name varchar(255) not null,
  optimization_goal varchar(80),
  billing_event varchar(80),
  bid_strategy varchar(80),
  destination_type varchar(80),
  status varchar(24) not null,
  provider_status varchar(40),
  effective_status varchar(80),
  daily_budget_minor bigint,
  lifetime_budget_minor bigint,
  bid_amount_minor bigint,
  start_at timestamp(6) with time zone,
  end_at timestamp(6) with time zone,
  targeting_json text,
  promoted_object_json text,
  attribution_spec_json text,
  provider_created_at timestamp(6) with time zone,
  provider_updated_at timestamp(6) with time zone,
  accessible boolean not null default true,
  provider_payload_json text,
  last_sync_at timestamp(6) with time zone,
  created_at timestamp(6) with time zone not null,
  updated_at timestamp(6) with time zone not null,
  constraint fk_ad_sets_company
    foreign key (company_id) references companies (id) on delete cascade,
  constraint fk_ad_sets_account
    foreign key (ad_account_id) references ad_accounts (id) on delete cascade,
  constraint fk_ad_sets_campaign
    foreign key (campaign_id) references ad_campaigns (id) on delete cascade,
  constraint uk_ad_set_provider_external
    unique (ad_account_id, provider, external_ad_set_id)
);

create index idx_ad_sets_company_campaign
  on ad_sets (company_id, campaign_id);

create index idx_ad_sets_campaign_status
  on ad_sets (campaign_id, status, accessible);
