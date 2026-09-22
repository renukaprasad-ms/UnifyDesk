create table message_templates (
  id bigint primary key,
  company_id bigint not null,
  name varchar(160) not null,
  channel_type varchar(24) not null,
  category varchar(32) not null,
  status varchar(24) not null,
  provider_status varchar(32) not null,
  provider_type varchar(40),
  external_template_id varchar(255),
  language varchar(32),
  subject varchar(998),
  header_text varchar(120),
  body_text text not null,
  body_html text,
  footer_text varchar(120),
  buttons_json text,
  variables_json text,
  created_by_user_id bigint not null,
  version bigint not null default 0,
  created_at timestamptz not null,
  updated_at timestamptz not null,
  archived_at timestamptz,
  constraint fk_message_templates_company
    foreign key (company_id) references companies(id) on delete cascade,
  constraint fk_message_templates_created_by
    foreign key (created_by_user_id) references users(id)
);

create index idx_message_templates_company_channel_status
  on message_templates(company_id, channel_type, status);

create index idx_message_templates_company_updated
  on message_templates(company_id, updated_at desc);

create unique index uk_message_templates_company_channel_name_active
  on message_templates(company_id, channel_type, lower(name))
  where status <> 'ARCHIVED';
