create table ad_account_resources (
  id bigint primary key,
  company_id bigint not null,
  ad_account_id bigint not null,
  provider varchar(24) not null,
  resource_type varchar(40) not null,
  external_resource_id varchar(255) not null,
  name varchar(255),
  accessible boolean not null default true,
  provider_payload_json text,
  last_sync_at timestamp(6) with time zone,
  created_at timestamp(6) with time zone not null,
  updated_at timestamp(6) with time zone not null,
  constraint fk_ad_account_resources_company
    foreign key (company_id) references companies (id) on delete cascade,
  constraint fk_ad_account_resources_ad_account
    foreign key (ad_account_id) references ad_accounts (id) on delete cascade,
  constraint uk_ad_account_resource_external
    unique (ad_account_id, resource_type, external_resource_id)
);

create index idx_ad_account_resources_account_type
  on ad_account_resources (ad_account_id, resource_type);

create index idx_ad_account_resources_company_access
  on ad_account_resources (company_id, accessible);
