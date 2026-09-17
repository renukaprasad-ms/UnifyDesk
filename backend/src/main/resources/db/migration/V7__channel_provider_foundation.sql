create table if not exists channel_connections (
  id bigint primary key,
  company_id bigint not null,
  channel_type varchar(24) not null,
  provider_type varchar(40) not null,
  name varchar(120) not null,
  external_account_id varchar(255),
  status varchar(24) not null,
  created_by_user_id bigint not null,
  last_sync_at timestamp(6) with time zone,
  created_at timestamp(6) with time zone not null,
  updated_at timestamp(6) with time zone not null,
  constraint fk_channel_connections_company
    foreign key (company_id) references companies (id),
  constraint fk_channel_connections_created_by
    foreign key (created_by_user_id) references users (id),
  constraint uk_channel_connection_provider_account
    unique (company_id, provider_type, external_account_id)
);

create table if not exists sender_identities (
  id bigint primary key,
  connection_id bigint not null,
  identity_type varchar(32) not null,
  value varchar(320) not null,
  external_id varchar(255),
  status varchar(24) not null,
  is_default boolean not null default false,
  created_at timestamp(6) with time zone not null,
  updated_at timestamp(6) with time zone not null,
  constraint fk_sender_identities_connection
    foreign key (connection_id) references channel_connections (id) on delete cascade,
  constraint uk_sender_identity_connection_type_value
    unique (connection_id, identity_type, value)
);

create table if not exists provider_credentials (
  id bigint primary key,
  connection_id bigint not null,
  credential_type varchar(40) not null,
  encrypted_payload text not null,
  expires_at timestamp(6) with time zone,
  created_at timestamp(6) with time zone not null,
  updated_at timestamp(6) with time zone not null,
  constraint fk_provider_credentials_connection
    foreign key (connection_id) references channel_connections (id) on delete cascade,
  constraint uk_provider_credential_connection_type
    unique (connection_id, credential_type)
);

create index if not exists idx_channel_connections_company
  on channel_connections (company_id);
create index if not exists idx_channel_connections_company_channel_status
  on channel_connections (company_id, channel_type, status);
create index if not exists idx_channel_connections_provider_external
  on channel_connections (provider_type, external_account_id);

create index if not exists idx_sender_identity_connection
  on sender_identities (connection_id);
create index if not exists idx_sender_identity_connection_default
  on sender_identities (connection_id, is_default);
create index if not exists idx_sender_identity_connection_status
  on sender_identities (connection_id, status);
create unique index if not exists uk_sender_identity_default_per_connection
  on sender_identities (connection_id)
  where is_default = true;

create index if not exists idx_provider_credentials_connection
  on provider_credentials (connection_id);
create index if not exists idx_provider_credentials_expires_at
  on provider_credentials (expires_at);
