alter table email_templates
  add column purpose varchar(24) not null default 'MARKETING';

alter table email_template_catalog
  add column purpose varchar(24) not null default 'MARKETING';

alter table email_template_versions
  add column purpose varchar(24) not null default 'MARKETING';

update email_template_catalog
set purpose = 'TRANSACTIONAL'
where slug = 'welcome';

create table email_template_provider_bindings (
  id bigint primary key,
  company_id bigint not null,
  template_id bigint not null,
  template_version_id bigint not null,
  template_version_number bigint not null,
  connection_id bigint not null,
  provider varchar(40) not null,
  provider_template_id varchar(255),
  provider_template_slug varchar(160) not null,
  status varchar(32) not null,
  provider_status varchar(128),
  error_code varchar(128),
  error_message varchar(1000),
  content_hash varchar(64) not null,
  synced_at timestamptz,
  last_checked_at timestamptz,
  created_at timestamptz not null,
  updated_at timestamptz not null,
  row_version bigint not null default 0,
  constraint fk_email_template_provider_binding_company
    foreign key (company_id) references companies(id) on delete cascade,
  constraint fk_email_template_provider_binding_template
    foreign key (template_id) references email_templates(id) on delete cascade,
  constraint fk_email_template_provider_binding_version
    foreign key (template_version_id) references email_template_versions(id) on delete cascade,
  constraint fk_email_template_provider_binding_connection
    foreign key (connection_id) references broadcast_sender_connections(id) on delete cascade,
  constraint uk_email_template_provider_binding_version_connection
    unique (template_version_id, connection_id, provider)
);

create index idx_email_template_provider_binding_company_template
  on email_template_provider_bindings(company_id, template_id);

create index idx_email_template_provider_binding_connection
  on email_template_provider_bindings(connection_id, provider, status);

alter table broadcast_runs
  add column provider_template_binding_id bigint,
  add column provider_template_id_snapshot varchar(255);

alter table broadcast_runs
  add constraint fk_broadcast_runs_provider_template_binding
  foreign key (provider_template_binding_id)
  references email_template_provider_bindings(id)
  on delete set null;
