alter table broadcast_messages
  add column source_template_id bigint,
  add column source_template_version bigint;

alter table broadcast_messages
  add constraint fk_broadcast_messages_source_template
  foreign key (source_template_id) references message_templates(id);

create index idx_broadcast_messages_source_template
  on broadcast_messages(source_template_id);
