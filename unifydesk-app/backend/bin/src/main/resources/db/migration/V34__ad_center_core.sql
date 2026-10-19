create table ad_provider_connections (
  id bigint primary key,
  company_id bigint not null,
  provider varchar(24) not null,
  external_user_id varchar(255),
  external_business_id varchar(255),
  display_name varchar(160),
  status varchar(24) not null,
  created_by_user_id bigint not null,
  last_sync_at timestamp(6) with time zone,
  last_error varchar(1000),
  version bigint not null default 0,
  created_at timestamp(6) with time zone not null,
  updated_at timestamp(6) with time zone not null,
  constraint fk_ad_provider_connections_company
    foreign key (company_id) references companies (id) on delete cascade,
  constraint fk_ad_provider_connections_created_by
    foreign key (created_by_user_id) references users (id),
  constraint uk_ad_provider_connection_business
    unique (company_id, provider, external_business_id)
);

create index idx_ad_provider_connections_company
  on ad_provider_connections (company_id);

create index idx_ad_provider_connections_company_provider_status
  on ad_provider_connections (company_id, provider, status);

create table ad_accounts (
  id bigint primary key,
  company_id bigint not null,
  connection_id bigint not null,
  provider varchar(24) not null,
  external_account_id varchar(255) not null,
  name varchar(160) not null,
  currency varchar(8),
  timezone varchar(80),
  status varchar(24) not null,
  primary_account boolean not null default false,
  provider_payload_json text,
  last_sync_at timestamp(6) with time zone,
  version bigint not null default 0,
  created_at timestamp(6) with time zone not null,
  updated_at timestamp(6) with time zone not null,
  constraint fk_ad_accounts_company
    foreign key (company_id) references companies (id) on delete cascade,
  constraint fk_ad_accounts_connection
    foreign key (connection_id) references ad_provider_connections (id) on delete cascade,
  constraint uk_ad_account_provider_external
    unique (company_id, provider, external_account_id)
);

create index idx_ad_accounts_company
  on ad_accounts (company_id);

create index idx_ad_accounts_company_provider_status
  on ad_accounts (company_id, provider, status);

create index idx_ad_accounts_connection
  on ad_accounts (connection_id);
