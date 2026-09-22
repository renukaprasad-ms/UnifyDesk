alter table broadcast_group_members
  drop constraint if exists uk_broadcast_group_member_destination;

create unique index if not exists uk_broadcast_group_member_active_destination
  on broadcast_group_members (group_id, normalized_destination)
  where status = 'ACTIVE';
