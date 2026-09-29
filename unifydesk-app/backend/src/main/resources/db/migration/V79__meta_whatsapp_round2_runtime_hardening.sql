-- Meta WhatsApp Round-2 runtime hardening.

create table if not exists meta_whatsapp_provider_media_reservations (
  id bigint primary key,
  connection_id bigint not null,
  inbox_message_id bigint not null,
  provider_media_id varchar(255) not null,
  reserved_media_id bigint not null,
  state varchar(24) not null,
  created_at timestamp(6) with time zone not null,
  updated_at timestamp(6) with time zone not null,
  completed_at timestamp(6) with time zone,
  constraint uk_meta_whatsapp_provider_media_reservation unique (connection_id, provider_media_id),
  constraint uk_meta_whatsapp_provider_media_reserved_id unique (reserved_media_id),
  constraint ck_meta_whatsapp_provider_media_reservation_state check (
    state in ('RESERVED','INGESTED','LINKED','FAILED')
  ),
  constraint fk_meta_whatsapp_provider_media_reservation_channel
    foreign key (connection_id) references channel_connections(id) on delete cascade,
  constraint fk_meta_whatsapp_provider_media_reservation_message
    foreign key (inbox_message_id) references inbox_messages(id) on delete cascade
);

create index if not exists idx_meta_whatsapp_provider_media_reservation_message
  on meta_whatsapp_provider_media_reservations (inbox_message_id, state);

create index if not exists idx_meta_whatsapp_runtime_stale_pending
  on meta_whatsapp_message_runtime (created_at, id)
  where direction='OUTBOUND'
    and detailed_status='PENDING'
    and provider_message_id is null;
