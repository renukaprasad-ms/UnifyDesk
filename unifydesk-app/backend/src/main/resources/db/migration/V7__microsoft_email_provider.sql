-- Track provider folder ownership for folder-scoped delta streams.
alter table inbox_messages
  add column if not exists provider_folder_key varchar(64);

create index if not exists idx_inbox_messages_connection_provider_folder
  on inbox_messages (connection_id, provider_folder_key);

-- Durable Microsoft Graph mailbox delta sync and webhook subscription state.

create table if not exists microsoft_sync_states (
  id bigint primary key,
  connection_id bigint not null,
  folder_key varchar(32) not null,
  status varchar(24) not null,
  next_url text,
  delta_url text,
  resync_requested boolean not null default false,
  next_attempt_at timestamp(6) with time zone,
  attempt_count integer not null default 0,
  last_error varchar(1000),
  claimed_at timestamp(6) with time zone,
  last_synced_at timestamp(6) with time zone,
  created_at timestamp(6) with time zone not null,
  updated_at timestamp(6) with time zone not null,
  constraint fk_microsoft_sync_states_connection
    foreign key (connection_id) references channel_connections(id) on delete cascade,
  constraint uk_microsoft_sync_state_connection_folder
    unique (connection_id, folder_key)
);

create index if not exists idx_microsoft_sync_states_claim
  on microsoft_sync_states (status, next_attempt_at, connection_id);

create index if not exists idx_microsoft_sync_states_connection
  on microsoft_sync_states (connection_id);

create index if not exists idx_microsoft_sync_states_safety
  on microsoft_sync_states (status, last_synced_at)
  where status = 'SYNCED';

create table if not exists microsoft_subscriptions (
  id bigint primary key,
  connection_id bigint not null,
  external_subscription_id varchar(255) not null,
  client_state varchar(255) not null,
  resource varchar(512) not null,
  expiration_at timestamp(6) with time zone not null,
  status varchar(24) not null,
  last_error varchar(1000),
  created_at timestamp(6) with time zone not null,
  updated_at timestamp(6) with time zone not null,
  constraint fk_microsoft_subscriptions_connection
    foreign key (connection_id) references channel_connections(id) on delete cascade,
  constraint uk_microsoft_subscription_connection unique (connection_id),
  constraint uk_microsoft_subscription_external unique (external_subscription_id)
);

create index if not exists idx_microsoft_subscriptions_expiration
  on microsoft_subscriptions (status, expiration_at);
