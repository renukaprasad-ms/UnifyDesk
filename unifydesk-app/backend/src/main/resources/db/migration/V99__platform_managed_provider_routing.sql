-- Global provider routing is super-admin controlled and changes only NEW managed runs.
create table platform_managed_provider_routes (
  channel varchar(16) primary key,
  provider_code varchar(40) not null,
  revision bigint not null default 1,
  updated_by bigint,
  updated_at timestamptz not null default now(),
  constraint ck_platform_managed_provider_route_channel check (channel in ('EMAIL','SMS')),
  constraint ck_platform_managed_provider_route_code check (
    (channel='EMAIL' and provider_code='AMAZON_SES') or
    (channel='SMS' and provider_code in ('MSG91_SMS','METAREACH_SMS')))
);
insert into platform_managed_provider_routes(channel,provider_code) values
 ('EMAIL','AMAZON_SES'),('SMS','MSG91_SMS')
on conflict (channel) do nothing;

create table platform_managed_provider_route_audit (
  id bigint primary key,
  channel varchar(16) not null,
  previous_provider varchar(40) not null,
  new_provider varchar(40) not null,
  previous_revision bigint not null,
  new_revision bigint not null,
  actor_user_id bigint not null references users(id),
  reason varchar(500) not null,
  changed_at timestamptz not null default now()
);
create index idx_platform_managed_provider_route_audit_at on platform_managed_provider_route_audit(changed_at desc);

-- Historical and in-flight runs remain MSG91; new runs snapshot selection once.
alter table managed_sms_run_bindings
  add column delivery_provider varchar(40) not null default 'MSG91_SMS';
alter table managed_sms_run_bindings
  add constraint ck_managed_sms_run_delivery_provider
  check (delivery_provider in ('MSG91_SMS','METAREACH_SMS'));
create index idx_managed_sms_run_delivery_provider on managed_sms_run_bindings(delivery_provider,created_at desc);
