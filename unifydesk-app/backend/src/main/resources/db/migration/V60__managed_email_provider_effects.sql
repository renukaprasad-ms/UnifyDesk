create table managed_email_provider_effects (
  id bigint primary key,
  event_id bigint not null,
  company_id bigint not null,
  tenant_id bigint not null,
  recipient_id bigint not null,
  effect_type varchar(48) not null,
  effect_reason varchar(32) not null,
  status varchar(24) not null,
  attempt_count integer not null default 0,
  next_attempt_at timestamp(6) with time zone not null,
  claimed_at timestamp(6) with time zone,
  completed_at timestamp(6) with time zone,
  last_error_code varchar(128),
  last_error_message varchar(1000),
  created_at timestamp(6) with time zone not null,
  updated_at timestamp(6) with time zone not null,

  constraint fk_me_provider_effect_event
    foreign key (event_id)
    references managed_email_provider_events(id)
    on delete restrict,

  constraint fk_me_provider_effect_company
    foreign key (company_id)
    references companies(id)
    on delete restrict,

  constraint fk_me_provider_effect_recipient
    foreign key (recipient_id)
    references broadcast_recipients(id)
    on delete restrict,

  constraint fk_me_provider_effect_tenant_company
    foreign key (tenant_id, company_id)
    references managed_email_tenants(id, company_id)
    on delete restrict,

  constraint uk_me_provider_effect
    unique (event_id, effect_type),

  constraint ck_me_provider_effect_type
    check (effect_type in ('TENANT_SUPPRESSION')),

  constraint ck_me_provider_effect_reason
    check (effect_reason in ('BOUNCE', 'COMPLAINT')),

  constraint ck_me_provider_effect_status
    check (status in ('PENDING', 'PROCESSING', 'RETRY', 'SUCCEEDED', 'FAILED')),

  constraint ck_me_provider_effect_attempt_count
    check (attempt_count >= 0)
);

create index idx_me_provider_effect_claim
  on managed_email_provider_effects(status, next_attempt_at, id);

create index idx_me_provider_effect_stale
  on managed_email_provider_effects(status, claimed_at)
  where status = 'PROCESSING';

create index idx_me_provider_effect_company
  on managed_email_provider_effects(company_id, status, updated_at desc);
