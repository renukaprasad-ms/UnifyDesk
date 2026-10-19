create table ad_audiences (
  id bigint primary key,
  company_id bigint not null,
  ad_account_id bigint not null,
  provider varchar(24) not null,
  external_audience_id varchar(255) not null,
  name varchar(255) not null,
  subtype varchar(80),
  description text,
  retention_days integer,
  approximate_count_lower_bound bigint,
  approximate_count_upper_bound bigint,
  delivery_status_json text,
  operation_status_json text,
  pixel_id varchar(255),
  rule_json text,
  lookalike_spec_json text,
  provider_created_at timestamp(6) with time zone,
  provider_updated_at timestamp(6) with time zone,
  accessible boolean not null default true,
  provider_payload_json text,
  last_sync_at timestamp(6) with time zone,
  created_at timestamp(6) with time zone not null,
  updated_at timestamp(6) with time zone not null,
  constraint fk_ad_audiences_company foreign key (company_id) references companies (id) on delete cascade,
  constraint fk_ad_audiences_account foreign key (ad_account_id) references ad_accounts (id) on delete cascade,
  constraint uk_ad_audience_provider_external unique (ad_account_id, provider, external_audience_id)
);

create index idx_ad_audiences_company_account on ad_audiences (company_id, ad_account_id);
create index idx_ad_audiences_account_subtype on ad_audiences (ad_account_id, subtype, accessible);

create table ad_conversions (
  id bigint primary key,
  company_id bigint not null,
  ad_account_id bigint not null,
  provider varchar(24) not null,
  external_conversion_id varchar(255) not null,
  name varchar(255) not null,
  description text,
  custom_event_type varchar(80),
  event_source_type varchar(80),
  pixel_id varchar(255),
  rule_json text,
  data_sources_json text,
  default_conversion_value numeric(19,4),
  archived boolean not null default false,
  unavailable boolean not null default false,
  first_fired_at timestamp(6) with time zone,
  last_fired_at timestamp(6) with time zone,
  provider_created_at timestamp(6) with time zone,
  accessible boolean not null default true,
  provider_payload_json text,
  last_sync_at timestamp(6) with time zone,
  created_at timestamp(6) with time zone not null,
  updated_at timestamp(6) with time zone not null,
  constraint fk_ad_conversions_company foreign key (company_id) references companies (id) on delete cascade,
  constraint fk_ad_conversions_account foreign key (ad_account_id) references ad_accounts (id) on delete cascade,
  constraint uk_ad_conversion_provider_external unique (ad_account_id, provider, external_conversion_id)
);

create index idx_ad_conversions_company_account on ad_conversions (company_id, ad_account_id);
create index idx_ad_conversions_account_archived on ad_conversions (ad_account_id, archived, accessible);
