create table ad_keywords (
  id bigint primary key,
  company_id bigint not null,
  ad_account_id bigint not null,
  campaign_id bigint not null,
  ad_set_id bigint not null,
  provider varchar(24) not null,
  external_keyword_id varchar(255) not null,
  keyword_text varchar(500) not null,
  match_type varchar(40),
  status varchar(24) not null,
  provider_status varchar(40),
  bid_amount_micros bigint,
  quality_score integer,
  final_urls_json text,
  negative boolean not null default false,
  accessible boolean not null default true,
  provider_payload_json text,
  last_sync_at timestamp(6) with time zone,
  created_at timestamp(6) with time zone not null,
  updated_at timestamp(6) with time zone not null,
  constraint fk_ad_keywords_company foreign key (company_id) references companies (id) on delete cascade,
  constraint fk_ad_keywords_account foreign key (ad_account_id) references ad_accounts (id) on delete cascade,
  constraint fk_ad_keywords_campaign foreign key (campaign_id) references ad_campaigns (id) on delete cascade,
  constraint fk_ad_keywords_ad_set foreign key (ad_set_id) references ad_sets (id) on delete cascade,
  constraint uk_ad_keyword_provider_external unique (ad_account_id, provider, external_keyword_id)
);

create index idx_ad_keywords_company_ad_set
  on ad_keywords (company_id, ad_set_id);

create index idx_ad_keywords_ad_set_status
  on ad_keywords (ad_set_id, status, accessible);
