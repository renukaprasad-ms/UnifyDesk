create table ad_account_health (
  id bigint primary key,
  company_id bigint not null,
  ad_account_id bigint not null,
  provider varchar(24) not null,
  account_status integer,
  disable_reason integer,
  opportunity_score numeric(9,4),
  opportunity_score_weight integer,
  page_authorized boolean,
  offsite_pixels_tos_accepted boolean,
  direct_deals_enabled boolean,
  notifications_enabled boolean,
  access_expires_at timestamp(6) with time zone,
  offsite_clo_signal_status integer,
  capabilities_json text,
  all_capabilities_json text,
  user_tasks_json text,
  failed_delivery_checks_json text,
  provider_payload_json text,
  last_sync_at timestamp(6) with time zone not null,
  created_at timestamp(6) with time zone not null,
  updated_at timestamp(6) with time zone not null,
  constraint fk_ad_account_health_company
    foreign key (company_id) references companies (id) on delete cascade,
  constraint fk_ad_account_health_account
    foreign key (ad_account_id) references ad_accounts (id) on delete cascade,
  constraint uk_ad_account_health
    unique (ad_account_id, provider)
);

create index idx_ad_account_health_company
  on ad_account_health (company_id, provider);
