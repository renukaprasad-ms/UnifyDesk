alter table broadcast_sender_connections
  add column channel_connection_id bigint;

insert into channel_connections (
  id,
  company_id,
  channel_type,
  provider_type,
  name,
  external_account_id,
  status,
  created_by_user_id,
  last_sync_at,
  disconnected_at,
  purge_after,
  deletion_requested_by,
  created_at,
  updated_at
)
select
  connection.id,
  connection.company_id,
  'EMAIL',
  'AMAZON_SES',
  connection.name,
  (
    select sender.from_address
    from broadcast_senders sender
    where sender.connection_id = connection.id
    order by sender.created_at asc
    limit 1
  ),
  case connection.status
    when 'ACTIVE' then 'ACTIVE'
    when 'ERROR' then 'ERROR'
    else 'DISCONNECTED'
  end,
  connection.created_by_user_id,
  connection.last_validated_at,
  case when connection.status = 'DISABLED' then connection.updated_at else null end,
  null,
  null,
  connection.created_at,
  connection.updated_at
from broadcast_sender_connections connection
on conflict (id) do nothing;

update broadcast_sender_connections
set channel_connection_id = id
where channel_connection_id is null;

alter table broadcast_sender_connections
  add constraint fk_broadcast_sender_connection_channel
    foreign key (channel_connection_id) references channel_connections (id) on delete set null,
  add constraint uk_broadcast_sender_connection_channel unique (channel_connection_id);

insert into sender_identities (
  id,
  connection_id,
  identity_type,
  value,
  external_id,
  status,
  is_default,
  created_at,
  updated_at
)
select
  sender.id,
  connection.channel_connection_id,
  'EMAIL_ADDRESS',
  sender.from_address,
  sender.id::text,
  case sender.status when 'ACTIVE' then 'ACTIVE' else 'DISABLED' end,
  true,
  sender.created_at,
  sender.updated_at
from broadcast_senders sender
join broadcast_sender_connections connection on connection.id = sender.connection_id
on conflict do nothing;
