create table ad_insight_breakdowns (
  id bigint primary key,
  company_id bigint not null,
  ad_account_id bigint not null,
  provider varchar(24) not null,
  insight_level varchar(24) not null,
  entity_ref varchar(255) not null,
  entity_name varchar(500),
  breakdown_type varchar(40) not null,
  dimension_value varchar(255) not null,
  dimension_value_2 varchar(255) not null default '',
  action_type varchar(255) not null default '',
  range_start date not null,
  range_end date not null,
  currency varchar(12),
  spend numeric(19,6) not null default 0,
  impressions bigint not null default 0,
  reach bigint not null default 0,
  clicks bigint not null default 0,
  ctr numeric(19,8),
  cpc numeric(19,8),
  cpm numeric(19,8),
  conversions numeric(19,6) not null default 0,
  conversion_value numeric(19,6) not null default 0,
  leads numeric(19,6) not null default 0,
  purchases numeric(19,6) not null default 0,
  action_value numeric(19,6) not null default 0,
  actions_json text,
  action_values_json text,
  provider_metrics_json text,
  last_sync_at timestamp(6) with time zone not null,
  created_at timestamp(6) with time zone not null,
  updated_at timestamp(6) with time zone not null,
  constraint fk_ad_insight_breakdowns_company
    foreign key (company_id) references companies (id) on delete cascade,
  constraint fk_ad_insight_breakdowns_account
    foreign key (ad_account_id) references ad_accounts (id) on delete cascade,
  constraint uk_ad_insight_breakdown
    unique (
      ad_account_id,
      provider,
      insight_level,
      entity_ref,
      breakdown_type,
      dimension_value,
      dimension_value_2,
      action_type,
      range_start,
      range_end
    )
);

create index idx_ad_insight_breakdowns_account_range
  on ad_insight_breakdowns (ad_account_id, breakdown_type, range_start, range_end);

create index idx_ad_insight_breakdowns_company_account
  on ad_insight_breakdowns (company_id, ad_account_id);
