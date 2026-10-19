create table ad_insights_daily (
  id bigint primary key,
  company_id bigint not null,
  ad_account_id bigint not null,
  campaign_id bigint,
  ad_set_id bigint,
  ad_id bigint,
  provider varchar(24) not null,
  insight_level varchar(24) not null,
  entity_ref varchar(255) not null,
  entity_name varchar(500),
  insight_date date not null,
  currency varchar(12),
  spend numeric(19,6) not null default 0,
  impressions bigint not null default 0,
  reach bigint not null default 0,
  clicks bigint not null default 0,
  unique_clicks bigint not null default 0,
  inline_link_clicks bigint not null default 0,
  ctr numeric(19,8),
  cpc numeric(19,8),
  cpm numeric(19,8),
  frequency numeric(19,8),
  conversions numeric(19,6) not null default 0,
  conversion_value numeric(19,6) not null default 0,
  leads numeric(19,6) not null default 0,
  purchases numeric(19,6) not null default 0,
  engagements numeric(19,6) not null default 0,
  video_views numeric(19,6) not null default 0,
  cpa numeric(19,8),
  roas numeric(19,8),
  actions_json text,
  action_values_json text,
  conversions_json text,
  conversion_values_json text,
  purchase_roas_json text,
  provider_metrics_json text,
  last_sync_at timestamp(6) with time zone not null,
  created_at timestamp(6) with time zone not null,
  updated_at timestamp(6) with time zone not null,
  constraint fk_ad_insights_company foreign key (company_id) references companies (id) on delete cascade,
  constraint fk_ad_insights_account foreign key (ad_account_id) references ad_accounts (id) on delete cascade,
  constraint fk_ad_insights_campaign foreign key (campaign_id) references ad_campaigns (id) on delete cascade,
  constraint fk_ad_insights_ad_set foreign key (ad_set_id) references ad_sets (id) on delete cascade,
  constraint fk_ad_insights_ad foreign key (ad_id) references ads (id) on delete cascade,
  constraint uk_ad_insights_daily unique (ad_account_id, provider, insight_level, entity_ref, insight_date)
);

create index idx_ad_insights_company_account_date
  on ad_insights_daily (company_id, ad_account_id, insight_date);
create index idx_ad_insights_account_level_date
  on ad_insights_daily (ad_account_id, insight_level, insight_date);
create index idx_ad_insights_campaign_date
  on ad_insights_daily (campaign_id, insight_date) where campaign_id is not null;
create index idx_ad_insights_ad_set_date
  on ad_insights_daily (ad_set_id, insight_date) where ad_set_id is not null;
create index idx_ad_insights_ad_date
  on ad_insights_daily (ad_id, insight_date) where ad_id is not null;
