create table if not exists crm_deal_attachments (
  id bigint primary key,
  company_id bigint not null references companies(id) on delete cascade,
  deal_id bigint not null,
  media_id bigint not null references media(id) on delete cascade,
  filename varchar(255) not null,
  content_type varchar(100),
  size_bytes bigint not null default 0,
  uploader_user_id bigint references users(id) on delete set null,
  created_at timestamptz not null default now(),
  foreign key (company_id, deal_id) references crm_deals(company_id, id) on delete cascade
);

create index if not exists idx_crm_deal_attachments_deal on crm_deal_attachments(company_id, deal_id, created_at desc);
create index if not exists idx_crm_deal_attachments_media on crm_deal_attachments(media_id);
