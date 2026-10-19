alter table broadcast_messages
  add column template_variables_json text not null default '{}';

alter table broadcast_runs
  add column template_variables_json_snapshot text not null default '{}';
