create table creative_packs (
  id bigint primary key,
  company_id bigint not null,
  reachlyo_campaign_id bigint not null,
  name varchar(255) not null,
  business_name varchar(255),
  offer_text text,
  primary_message text,
  call_to_action varchar(80),
  destination_url varchar(2048),
  headlines_json text,
  descriptions_json text,
  created_by_user_id bigint not null,
  version bigint not null default 0,
  created_at timestamp(6) with time zone not null,
  updated_at timestamp(6) with time zone not null,
  constraint fk_creative_packs_company foreign key (company_id) references companies (id) on delete cascade,
  constraint fk_creative_packs_campaign foreign key (reachlyo_campaign_id) references reachlyo_campaigns (id) on delete cascade
);
create index idx_creative_packs_company_campaign on creative_packs (company_id, reachlyo_campaign_id);

create table creative_pack_assets (
  id bigint primary key,
  company_id bigint not null,
  creative_pack_id bigint not null,
  media_id bigint not null,
  role varchar(24) not null,
  label varchar(160),
  sort_order integer not null default 0,
  created_at timestamp(6) with time zone not null,
  constraint fk_creative_pack_assets_company foreign key (company_id) references companies (id) on delete cascade,
  constraint fk_creative_pack_assets_pack foreign key (creative_pack_id) references creative_packs (id) on delete cascade,
  constraint fk_creative_pack_assets_media foreign key (media_id) references media (id),
  constraint uk_creative_pack_asset_media_role unique (creative_pack_id, media_id, role)
);
create index idx_creative_pack_assets_pack on creative_pack_assets (creative_pack_id);
create index idx_creative_pack_assets_media on creative_pack_assets (media_id);

create table creative_variants (
  id bigint primary key,
  company_id bigint not null,
  reachlyo_campaign_id bigint not null,
  creative_pack_id bigint not null,
  campaign_channel_id bigint,
  provider varchar(24) not null,
  variant_type varchar(48) not null,
  origin varchar(24) not null,
  status varchar(24) not null,
  name varchar(255) not null,
  placement varchar(120),
  primary_text text,
  headlines_json text,
  descriptions_json text,
  call_to_action varchar(80),
  destination_url varchar(2048),
  asset_spec_json text,
  version bigint not null default 0,
  created_at timestamp(6) with time zone not null,
  updated_at timestamp(6) with time zone not null,
  constraint fk_creative_variants_company foreign key (company_id) references companies (id) on delete cascade,
  constraint fk_creative_variants_campaign foreign key (reachlyo_campaign_id) references reachlyo_campaigns (id) on delete cascade,
  constraint fk_creative_variants_pack foreign key (creative_pack_id) references creative_packs (id) on delete cascade,
  constraint fk_creative_variants_channel foreign key (campaign_channel_id) references campaign_channels (id) on delete cascade
);
create index idx_creative_variants_pack on creative_variants (creative_pack_id);
create index idx_creative_variants_channel on creative_variants (campaign_channel_id);
create index idx_creative_variants_provider_type on creative_variants (provider, variant_type);

create table audience_definitions (
  id bigint primary key,
  company_id bigint not null,
  reachlyo_campaign_id bigint not null,
  name varchar(255) not null,
  description text,
  age_min integer,
  age_max integer,
  locations_json text,
  languages_json text,
  genders_json text,
  customer_segments_json text,
  retargeting_json text,
  exclusions_json text,
  interests_json text,
  search_intent_json text,
  created_by_user_id bigint not null,
  version bigint not null default 0,
  created_at timestamp(6) with time zone not null,
  updated_at timestamp(6) with time zone not null,
  constraint fk_audience_definitions_company foreign key (company_id) references companies (id) on delete cascade,
  constraint fk_audience_definitions_campaign foreign key (reachlyo_campaign_id) references reachlyo_campaigns (id) on delete cascade,
  constraint ck_audience_definition_age_min check (age_min is null or age_min >= 13),
  constraint ck_audience_definition_age_max check (age_max is null or age_max >= 13),
  constraint ck_audience_definition_age_range check (age_min is null or age_max is null or age_max >= age_min)
);
create index idx_audience_definitions_company_campaign on audience_definitions (company_id, reachlyo_campaign_id);

create table audience_mappings (
  id bigint primary key,
  company_id bigint not null,
  reachlyo_campaign_id bigint not null,
  audience_definition_id bigint not null,
  campaign_channel_id bigint not null,
  provider varchar(24) not null,
  mapping_status varchar(24) not null,
  local_audience_id bigint,
  external_audience_id varchar(255),
  provider_criteria_json text,
  notes text,
  version bigint not null default 0,
  created_at timestamp(6) with time zone not null,
  updated_at timestamp(6) with time zone not null,
  constraint fk_audience_mappings_company foreign key (company_id) references companies (id) on delete cascade,
  constraint fk_audience_mappings_campaign foreign key (reachlyo_campaign_id) references reachlyo_campaigns (id) on delete cascade,
  constraint fk_audience_mappings_definition foreign key (audience_definition_id) references audience_definitions (id) on delete cascade,
  constraint fk_audience_mappings_channel foreign key (campaign_channel_id) references campaign_channels (id) on delete cascade,
  constraint fk_audience_mappings_local_audience foreign key (local_audience_id) references ad_audiences (id) on delete set null,
  constraint uk_audience_mapping_definition_channel unique (audience_definition_id, campaign_channel_id)
);
create index idx_audience_mappings_definition on audience_mappings (audience_definition_id);
create index idx_audience_mappings_channel on audience_mappings (campaign_channel_id);

create table conversion_goal_mappings (
  id bigint primary key,
  company_id bigint not null,
  reachlyo_campaign_id bigint not null,
  business_goal varchar(40) not null,
  provider varchar(24) not null,
  ad_account_id bigint not null,
  target_type varchar(32) not null,
  ad_conversion_id bigint,
  provider_event_name varchar(120),
  external_conversion_id varchar(255),
  validated boolean not null default false,
  version bigint not null default 0,
  created_at timestamp(6) with time zone not null,
  updated_at timestamp(6) with time zone not null,
  constraint fk_conversion_goal_mappings_company foreign key (company_id) references companies (id) on delete cascade,
  constraint fk_conversion_goal_mappings_campaign foreign key (reachlyo_campaign_id) references reachlyo_campaigns (id) on delete cascade,
  constraint fk_conversion_goal_mappings_account foreign key (ad_account_id) references ad_accounts (id),
  constraint fk_conversion_goal_mappings_conversion foreign key (ad_conversion_id) references ad_conversions (id) on delete cascade,
  constraint ck_conversion_goal_target check (
    (target_type = 'PROVIDER_CONVERSION' and ad_conversion_id is not null)
    or (target_type = 'STANDARD_EVENT' and provider_event_name is not null)
  ),
  constraint uk_conversion_goal_campaign_provider_account unique (reachlyo_campaign_id, provider, ad_account_id, business_goal)
);
create index idx_conversion_goal_mappings_campaign on conversion_goal_mappings (company_id, reachlyo_campaign_id);
create index idx_conversion_goal_mappings_account on conversion_goal_mappings (ad_account_id);
