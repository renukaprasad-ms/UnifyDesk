create table ad_activity_logs (
  id bigint primary key,
  company_id bigint not null,
  ad_account_id bigint,
  provider varchar(24) not null,
  actor_user_id bigint,
  action varchar(120) not null,
  entity_type varchar(80) not null,
  entity_id varchar(255),
  before_json text,
  after_json text,
  metadata_json text,
  created_at timestamp(6) with time zone not null,
  constraint fk_ad_activity_company
    foreign key (company_id) references companies (id) on delete cascade,
  constraint fk_ad_activity_account
    foreign key (ad_account_id) references ad_accounts (id) on delete set null
);

create index idx_ad_activity_company_created
  on ad_activity_logs (company_id, created_at desc);

create index idx_ad_activity_account_created
  on ad_activity_logs (ad_account_id, created_at desc)
  where ad_account_id is not null;
