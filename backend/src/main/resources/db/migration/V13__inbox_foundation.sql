create table if not exists inbox_conversations (
  id bigint primary key,
  company_id bigint not null,
  connection_id bigint not null,
  provider_type varchar(40) not null,
  external_conversation_id varchar(255) not null,
  subject varchar(998),
  last_message_preview text,
  last_message_at timestamp(6) with time zone,
  unread_count integer not null default 0,
  status varchar(24) not null,
  created_at timestamp(6) with time zone not null,
  updated_at timestamp(6) with time zone not null,
  constraint fk_inbox_conversations_company foreign key (company_id) references companies (id) on delete cascade,
  constraint fk_inbox_conversations_connection foreign key (connection_id) references channel_connections (id) on delete cascade,
  constraint uk_inbox_conversation_external unique (connection_id, external_conversation_id)
);

create table if not exists inbox_participants (
  id bigint primary key,
  conversation_id bigint not null,
  participant_type varchar(32) not null,
  address varchar(320) not null,
  display_name varchar(255),
  created_at timestamp(6) with time zone not null,
  updated_at timestamp(6) with time zone not null,
  constraint fk_inbox_participants_conversation foreign key (conversation_id) references inbox_conversations (id) on delete cascade,
  constraint uk_inbox_participant_address unique (conversation_id, participant_type, address)
);

create table if not exists inbox_messages (
  id bigint primary key,
  conversation_id bigint not null,
  connection_id bigint not null,
  external_message_id varchar(255) not null,
  internet_message_id varchar(998),
  provider_history_id varchar(255),
  direction varchar(24) not null,
  status varchar(24) not null,
  subject varchar(998),
  snippet text,
  body_text text,
  sent_at timestamp(6) with time zone,
  is_read boolean not null default true,
  created_at timestamp(6) with time zone not null,
  updated_at timestamp(6) with time zone not null,
  constraint fk_inbox_messages_conversation foreign key (conversation_id) references inbox_conversations (id) on delete cascade,
  constraint fk_inbox_messages_connection foreign key (connection_id) references channel_connections (id) on delete cascade,
  constraint uk_inbox_message_external unique (connection_id, external_message_id)
);

create table if not exists inbox_message_attachments (
  id bigint primary key,
  message_id bigint not null,
  external_attachment_id varchar(255) not null,
  filename varchar(512),
  mime_type varchar(255),
  size_bytes bigint,
  created_at timestamp(6) with time zone not null,
  updated_at timestamp(6) with time zone not null,
  constraint fk_inbox_message_attachments_message foreign key (message_id) references inbox_messages (id) on delete cascade,
  constraint uk_inbox_message_attachment_external unique (message_id, external_attachment_id)
);

create table if not exists channel_sync_states (
  id bigint primary key,
  connection_id bigint not null,
  provider_type varchar(40) not null,
  cursor_value varchar(1024),
  last_full_sync_at timestamp(6) with time zone,
  last_incremental_sync_at timestamp(6) with time zone,
  created_at timestamp(6) with time zone not null,
  updated_at timestamp(6) with time zone not null,
  constraint fk_channel_sync_states_connection foreign key (connection_id) references channel_connections (id) on delete cascade,
  constraint uk_channel_sync_state_connection_provider unique (connection_id, provider_type)
);

create index if not exists idx_inbox_conversations_company_last_message on inbox_conversations (company_id, last_message_at desc);
create index if not exists idx_inbox_conversations_connection on inbox_conversations (connection_id);
create index if not exists idx_inbox_messages_conversation_sent on inbox_messages (conversation_id, sent_at);
create index if not exists idx_inbox_messages_connection on inbox_messages (connection_id);
create index if not exists idx_inbox_messages_unread on inbox_messages (conversation_id, is_read) where is_read = false;
create index if not exists idx_inbox_participants_conversation on inbox_participants (conversation_id);
create index if not exists idx_inbox_attachments_message on inbox_message_attachments (message_id);
create index if not exists idx_channel_sync_states_connection on channel_sync_states (connection_id);
