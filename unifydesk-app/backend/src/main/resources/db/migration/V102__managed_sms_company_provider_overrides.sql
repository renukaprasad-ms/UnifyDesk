-- A super-admin may migrate one approved company without affecting other tenants.
create table platform_managed_provider_company_routes (
  company_id bigint not null references companies(id),
  channel varchar(16) not null,
  provider_code varchar(40) not null,
  revision bigint not null default 1,
  changed_by bigint not null references users(id),
  reason varchar(500) not null,
  changed_at timestamptz not null default now(),
  primary key (company_id,channel),
  constraint ck_managed_company_channel check(channel='SMS'),
  constraint ck_managed_company_provider check(provider_code in ('MSG91_SMS','METAREACH_SMS'))
);
