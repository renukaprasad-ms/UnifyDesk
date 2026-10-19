create table email_template_assets (
  id bigint primary key,
  company_id bigint not null,
  media_id bigint not null,
  public_token varchar(64) not null,
  created_at timestamptz not null,
  constraint fk_email_template_assets_company
    foreign key (company_id) references companies(id) on delete cascade,
  constraint fk_email_template_assets_media
    foreign key (media_id) references media(id),
  constraint uk_email_template_assets_token unique (public_token),
  constraint uk_email_template_assets_company_media unique (company_id, media_id)
);

create index idx_email_template_assets_company
  on email_template_assets(company_id, created_at desc);
