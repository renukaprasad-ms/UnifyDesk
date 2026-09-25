create table ad_creatives (
  id bigint primary key,
  company_id bigint not null,
  ad_account_id bigint not null,
  provider varchar(24) not null,
  external_creative_id varchar(255) not null,
  name varchar(255) not null,
  creative_format varchar(40),
  page_id varchar(255),
  instagram_actor_id varchar(255),
  primary_text text,
  headline varchar(500),
  description text,
  call_to_action_type varchar(80),
  destination_url text,
  image_hash varchar(255),
  image_url text,
  video_id varchar(255),
  url_tags text,
  object_story_spec_json text,
  carousel_json text,
  provider_created_at timestamp(6) with time zone,
  provider_updated_at timestamp(6) with time zone,
  accessible boolean not null default true,
  provider_payload_json text,
  last_sync_at timestamp(6) with time zone,
  created_at timestamp(6) with time zone not null,
  updated_at timestamp(6) with time zone not null,
  constraint fk_ad_creatives_company
    foreign key (company_id) references companies (id) on delete cascade,
  constraint fk_ad_creatives_account
    foreign key (ad_account_id) references ad_accounts (id) on delete cascade,
  constraint uk_ad_creative_provider_external
    unique (ad_account_id, provider, external_creative_id)
);

create index idx_ad_creatives_company_account
  on ad_creatives (company_id, ad_account_id);

create table ads (
  id bigint primary key,
  company_id bigint not null,
  ad_account_id bigint not null,
  campaign_id bigint not null,
  ad_set_id bigint not null,
  creative_id bigint,
  provider varchar(24) not null,
  external_ad_id varchar(255) not null,
  external_creative_id varchar(255),
  name varchar(255) not null,
  status varchar(24) not null,
  provider_status varchar(40),
  effective_status varchar(80),
  tracking_specs_json text,
  conversion_specs_json text,
  provider_created_at timestamp(6) with time zone,
  provider_updated_at timestamp(6) with time zone,
  accessible boolean not null default true,
  provider_payload_json text,
  last_sync_at timestamp(6) with time zone,
  created_at timestamp(6) with time zone not null,
  updated_at timestamp(6) with time zone not null,
  constraint fk_ads_company
    foreign key (company_id) references companies (id) on delete cascade,
  constraint fk_ads_account
    foreign key (ad_account_id) references ad_accounts (id) on delete cascade,
  constraint fk_ads_campaign
    foreign key (campaign_id) references ad_campaigns (id) on delete cascade,
  constraint fk_ads_ad_set
    foreign key (ad_set_id) references ad_sets (id) on delete cascade,
  constraint fk_ads_creative
    foreign key (creative_id) references ad_creatives (id) on delete set null,
  constraint uk_ad_provider_external
    unique (ad_account_id, provider, external_ad_id)
);

create index idx_ads_company_ad_set
  on ads (company_id, ad_set_id);

create index idx_ads_ad_set_status
  on ads (ad_set_id, status, accessible);
