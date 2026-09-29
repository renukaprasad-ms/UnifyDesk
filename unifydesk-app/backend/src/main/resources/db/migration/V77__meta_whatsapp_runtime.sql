-- Meta WhatsApp runtime plane. Independent of V76 control-plane tables by design.

create table if not exists meta_whatsapp_webhook_events (
  id bigint primary key,
  event_key varchar(255) not null,
  provider_object_id varchar(255),
  phone_number_id varchar(255),
  provider_message_id varchar(255),
  event_kind varchar(32) not null,
  provider_event_at timestamp(6) with time zone,
  received_at timestamp(6) with time zone not null,
  payload_json jsonb not null,
  state varchar(24) not null,
  attempt_count integer not null default 0,
  next_attempt_at timestamp(6) with time zone not null,
  claim_token varchar(80),
  claim_expires_at timestamp(6) with time zone,
  last_error_code varchar(80),
  processed_at timestamp(6) with time zone,
  constraint uk_meta_whatsapp_webhook_event_key unique (event_key),
  constraint ck_meta_whatsapp_webhook_event_state check (
    state in ('PENDING','CLAIMED','PROCESSED','IGNORED','FAILED_RETRYABLE','DEAD')
  )
);

create index if not exists idx_meta_whatsapp_webhook_due
  on meta_whatsapp_webhook_events (next_attempt_at, id)
  where state in ('PENDING','FAILED_RETRYABLE','CLAIMED');

create index if not exists idx_meta_whatsapp_webhook_phone
  on meta_whatsapp_webhook_events (phone_number_id, received_at desc);

create index if not exists idx_meta_whatsapp_webhook_provider_message
  on meta_whatsapp_webhook_events (provider_message_id, received_at desc);

create index if not exists idx_meta_whatsapp_webhook_received
  on meta_whatsapp_webhook_events (received_at desc);

create table if not exists meta_whatsapp_message_runtime (
  id bigint primary key,
  company_id bigint not null,
  connection_id bigint not null,
  inbox_message_id bigint,
  provider_message_id varchar(255),
  client_request_id varchar(120),
  direction varchar(16) not null,
  message_kind varchar(24) not null,
  detailed_status varchar(24) not null,
  status_rank integer not null default 0,
  provider_status_at timestamp(6) with time zone,
  last_provider_error_code integer,
  last_provider_error_subcode integer,
  last_provider_fbtrace_id varchar(255),
  created_at timestamp(6) with time zone not null,
  updated_at timestamp(6) with time zone not null,
  constraint ck_meta_whatsapp_message_direction check (direction in ('INBOUND','OUTBOUND')),
  constraint ck_meta_whatsapp_message_status check (
    detailed_status in ('PENDING','ACCEPTED','SENT','DELIVERED','READ','FAILED','UNKNOWN')
  )
);

create unique index if not exists uk_meta_whatsapp_runtime_provider_message
  on meta_whatsapp_message_runtime (connection_id, provider_message_id)
  where provider_message_id is not null;

create unique index if not exists uk_meta_whatsapp_runtime_client_request
  on meta_whatsapp_message_runtime (company_id, connection_id, client_request_id)
  where client_request_id is not null;

create index if not exists idx_meta_whatsapp_runtime_inbox_message
  on meta_whatsapp_message_runtime (inbox_message_id);

create index if not exists idx_meta_whatsapp_runtime_company_updated
  on meta_whatsapp_message_runtime (company_id, connection_id, created_at desc);

create table if not exists meta_whatsapp_template_cache (
  id bigint primary key,
  company_id bigint not null,
  connection_id bigint not null,
  provider_template_id varchar(255),
  name varchar(512) not null,
  language varchar(40) not null,
  category varchar(40),
  status varchar(40) not null,
  components_json jsonb not null,
  synced_at timestamp(6) with time zone not null,
  updated_at timestamp(6) with time zone not null,
  constraint uk_meta_whatsapp_template_name_language unique (connection_id, name, language)
);

create index if not exists idx_meta_whatsapp_template_connection_status
  on meta_whatsapp_template_cache (company_id, connection_id, status);

create index if not exists idx_meta_whatsapp_template_company
  on meta_whatsapp_template_cache (synced_at);

create table if not exists meta_whatsapp_customer_sessions (
  id bigint primary key,
  company_id bigint not null,
  connection_id bigint not null,
  customer_key varchar(255) not null,
  last_customer_message_at timestamp(6) with time zone not null,
  updated_at timestamp(6) with time zone not null,
  constraint uk_meta_whatsapp_customer_session unique (connection_id, customer_key)
);

create index if not exists idx_meta_whatsapp_customer_session_company
  on meta_whatsapp_customer_sessions (company_id, updated_at);

create table if not exists meta_whatsapp_message_media (
  id bigint primary key,
  message_id bigint not null,
  provider_media_id varchar(255) not null,
  media_id bigint not null,
  direction varchar(16) not null,
  created_at timestamp(6) with time zone not null,
  constraint ck_meta_whatsapp_message_media_direction check (direction in ('INBOUND','OUTBOUND')),
  constraint uk_meta_whatsapp_message_provider_media unique (message_id, provider_media_id)
);

create unique index if not exists uk_meta_whatsapp_message_media_local
  on meta_whatsapp_message_media (message_id, media_id);

-- Existing generic tables are hot-path dependencies but are intentionally not constrained to V76.
create index if not exists idx_sender_identities_external_id
  on sender_identities (external_id)
  where external_id is not null;

create index if not exists idx_inbox_messages_whatsapp_folder
  on inbox_messages (connection_id, sent_at desc)
  where provider_folder_key = 'WHATSAPP';
