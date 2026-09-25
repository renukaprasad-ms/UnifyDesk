alter table ad_provider_connections
  add column provider_payload_json text;

create unique index uk_ad_provider_connection_user
  on ad_provider_connections (company_id, provider, external_user_id)
  where external_user_id is not null;

alter table ad_accounts
  add column external_business_id varchar(255),
  add column managed boolean not null default false;

create table ad_provider_credentials (
  id bigint primary key,
  connection_id bigint not null,
  credential_type varchar(40) not null,
  encrypted_payload text not null,
  expires_at timestamp(6) with time zone,
  created_at timestamp(6) with time zone not null,
  updated_at timestamp(6) with time zone not null,
  constraint fk_ad_provider_credentials_connection
    foreign key (connection_id) references ad_provider_connections (id) on delete cascade,
  constraint uk_ad_provider_credential_connection_type
    unique (connection_id, credential_type)
);

create index idx_ad_provider_credentials_connection
  on ad_provider_credentials (connection_id);

create index idx_ad_provider_credentials_expires_at
  on ad_provider_credentials (expires_at);
