-- Immutable Super Admin history for company overrides and PE/TM authorizations.
create table platform_managed_sms_company_route_events (
 id bigint primary key,
 company_id bigint not null references companies(id),
 previous_provider varchar(40) not null,
 new_provider varchar(40) not null,
 changed_by bigint not null references users(id),
 reason varchar(500) not null,
 changed_at timestamptz not null default now()
);
create index idx_company_sms_route_events on platform_managed_sms_company_route_events(company_id,changed_at desc);
create table managed_sms_metareach_company_approval_events (
 id bigint primary key,
 company_id bigint not null references companies(id),
 pe_id varchar(100),
 action varchar(16) not null check(action in ('APPROVED','REVOKED')),
 actor_user_id bigint not null references users(id),
 reason varchar(500) not null,
 changed_at timestamptz not null default now()
);
create index idx_meta_approval_events on managed_sms_metareach_company_approval_events(company_id,changed_at desc);
