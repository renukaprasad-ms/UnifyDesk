create table broadcast_setup_sessions (
  id bigint primary key,
  company_id bigint not null,
  channel_type varchar(32) not null,
  provider varchar(40) not null,
  current_step integer not null default 0,
  status varchar(32) not null,
  setup_data_json text,
  created_by_user_id bigint not null,
  created_at timestamptz not null,
  updated_at timestamptz not null,
  completed_at timestamptz
);

create index idx_broadcast_setup_sessions_company_channel
  on broadcast_setup_sessions(company_id, channel_type, updated_at desc);
