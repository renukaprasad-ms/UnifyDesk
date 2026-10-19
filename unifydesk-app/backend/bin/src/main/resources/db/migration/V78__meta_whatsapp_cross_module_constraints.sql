-- Lead-owned Meta WhatsApp cross-module constraints.
-- Applied only after B1 V76 and B2 V77 have been integrated.

alter table meta_whatsapp_connections
  add constraint fk_meta_whatsapp_connections_channel
  foreign key (connection_id) references channel_connections(id) on delete cascade;

alter table meta_whatsapp_connections
  add constraint fk_meta_whatsapp_connections_company
  foreign key (company_id) references companies(id) on delete cascade;

alter table meta_whatsapp_provisioning_operations
  add constraint fk_meta_whatsapp_provisioning_channel
  foreign key (connection_id) references channel_connections(id) on delete cascade;

alter table meta_whatsapp_provisioning_operations
  add constraint fk_meta_whatsapp_provisioning_company
  foreign key (company_id) references companies(id) on delete cascade;

alter table meta_whatsapp_message_runtime
  add constraint fk_meta_whatsapp_runtime_company
  foreign key (company_id) references companies(id) on delete cascade;

alter table meta_whatsapp_message_runtime
  add constraint fk_meta_whatsapp_runtime_channel
  foreign key (connection_id) references channel_connections(id) on delete cascade;

alter table meta_whatsapp_message_runtime
  add constraint fk_meta_whatsapp_runtime_inbox_message
  foreign key (inbox_message_id) references inbox_messages(id) on delete cascade;

alter table meta_whatsapp_template_cache
  add constraint fk_meta_whatsapp_template_company
  foreign key (company_id) references companies(id) on delete cascade;

alter table meta_whatsapp_template_cache
  add constraint fk_meta_whatsapp_template_channel
  foreign key (connection_id) references channel_connections(id) on delete cascade;

alter table meta_whatsapp_customer_sessions
  add constraint fk_meta_whatsapp_customer_session_company
  foreign key (company_id) references companies(id) on delete cascade;

alter table meta_whatsapp_customer_sessions
  add constraint fk_meta_whatsapp_customer_session_channel
  foreign key (connection_id) references channel_connections(id) on delete cascade;

alter table meta_whatsapp_message_media
  add constraint fk_meta_whatsapp_message_media_message
  foreign key (message_id) references inbox_messages(id) on delete cascade;

alter table meta_whatsapp_message_media
  add constraint fk_meta_whatsapp_message_media_media
  foreign key (media_id) references media(id) on delete cascade;
