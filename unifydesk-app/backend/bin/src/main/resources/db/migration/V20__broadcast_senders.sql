create table broadcast_sender_connections (
  id bigint primary key,
  company_id bigint not null,
  provider varchar(40) not null,
  management_mode varchar(32) not null,
  auth_mode varchar(32) not null,
  name varchar(160) not null,
  region varchar(64) not null,
  role_arn varchar(512),
  external_id varchar(255),
  encrypted_credentials text,
  status varchar(24) not null,
  production_access_enabled boolean not null default false,
  sending_enabled boolean not null default false,
  max_24h_send double precision,
  max_send_rate double precision,
  sent_last_24h double precision,
  last_validated_at timestamp(6) with time zone,
  created_by_user_id bigint not null,
  version bigint not null default 0,
  created_at timestamp(6) with time zone not null,
  updated_at timestamp(6) with time zone not null,
  constraint fk_broadcast_sender_connections_company
    foreign key (company_id) references companies (id) on delete cascade,
  constraint fk_broadcast_sender_connections_created_by
    foreign key (created_by_user_id) references users (id)
);

create index idx_broadcast_sender_connections_company_provider
  on broadcast_sender_connections (company_id, provider, status, updated_at desc);

create table broadcast_senders (
  id bigint primary key,
  company_id bigint not null,
  connection_id bigint not null,
  provider varchar(40) not null,
  from_address varchar(320) not null,
  display_name varchar(255),
  reply_to_address varchar(320),
  status varchar(24) not null,
  verified_identity varchar(320) not null,
  created_at timestamp(6) with time zone not null,
  updated_at timestamp(6) with time zone not null,
  constraint fk_broadcast_senders_company
    foreign key (company_id) references companies (id) on delete cascade,
  constraint fk_broadcast_senders_connection
    foreign key (connection_id) references broadcast_sender_connections (id) on delete cascade,
  constraint uk_broadcast_sender_address
    unique (company_id, provider, from_address)
);

create index idx_broadcast_senders_company_status
  on broadcast_senders (company_id, status, updated_at desc);

alter table broadcast_messages
  alter column connection_id drop not null,
  add column sender_id bigint,
  add constraint fk_broadcast_messages_sender
    foreign key (sender_id) references broadcast_senders (id);

create index idx_broadcast_messages_sender
  on broadcast_messages (sender_id)
  where sender_id is not null;

alter table broadcast_runs
  alter column connection_id drop not null,
  add column sender_id bigint,
  add constraint fk_broadcast_runs_sender
    foreign key (sender_id) references broadcast_senders (id);

create index idx_broadcast_runs_sender
  on broadcast_runs (sender_id)
  where sender_id is not null;

drop index if exists idx_broadcast_recipients_bounce_fallback;

alter table broadcast_recipients
  alter column connection_id drop not null,
  add column sender_id bigint,
  add constraint fk_broadcast_recipients_sender
    foreign key (sender_id) references broadcast_senders (id);

create index idx_broadcast_recipients_sender
  on broadcast_recipients (sender_id, status, created_at desc)
  where sender_id is not null;
