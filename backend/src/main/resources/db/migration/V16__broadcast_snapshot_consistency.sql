alter table broadcast_group_members
  add column active_since timestamp(6) with time zone,
  add column disabled_at timestamp(6) with time zone;

update broadcast_group_members
set active_since = created_at
where active_since is null;

alter table broadcast_group_members
  alter column active_since set not null;

alter table broadcast_runs
  add column snapshot_max_member_id bigint;

create index idx_broadcast_group_members_snapshot
  on broadcast_group_members (group_id, id, active_since, disabled_at);
