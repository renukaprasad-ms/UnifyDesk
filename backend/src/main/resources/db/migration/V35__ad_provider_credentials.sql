create table ad_provider_credentials (
  id bigint primary key,
  connection_id bigint not null unique,
  encrypted_access_token text not null,
  expires_at timestamp(6) with time zone,
  created_at timestamp(6) with time zone not null,
  updated_at timestamp(6) with time zone not null,
  constraint fk_ad_provider_credentials_connection
    foreign key (connection_id) references ad_provider_connections (id) on delete cascade
);

create index idx_ad_provider_credentials_connection
  on ad_provider_credentials (connection_id);
