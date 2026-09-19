-- Company-wide logical storage accounting.

alter table inbox_messages
  add column if not exists storage_bytes bigint not null default 0;

create table if not exists company_storage_usage (
  company_id bigint not null,
  usage_type varchar(48) not null,
  used_bytes bigint not null default 0,
  reserved_bytes bigint not null default 0,
  updated_at timestamp(6) with time zone not null default now(),
  primary key (company_id, usage_type),
  constraint fk_company_storage_usage_company
    foreign key (company_id) references companies (id)
);

create table if not exists storage_usage_events (
  id bigint primary key,
  company_id bigint not null,
  usage_type varchar(48) not null,
  resource_type varchar(64) not null,
  resource_id varchar(160) not null,
  event_type varchar(32) not null,
  used_bytes_delta bigint not null,
  reserved_bytes_delta bigint not null,
  idempotency_key varchar(320) not null,
  created_at timestamp(6) with time zone not null,
  constraint fk_storage_usage_events_company
    foreign key (company_id) references companies (id),
  constraint uk_storage_usage_events_idempotency unique (idempotency_key)
);

create index if not exists idx_storage_usage_events_company_created
  on storage_usage_events (company_id, created_at);

create index if not exists idx_storage_usage_events_resource
  on storage_usage_events (resource_type, resource_id);

create index if not exists idx_inbox_messages_storage_bytes
  on inbox_messages (storage_bytes);
