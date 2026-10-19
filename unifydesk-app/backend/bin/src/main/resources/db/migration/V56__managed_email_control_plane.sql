create unique index if not exists uk_broadcast_sender_connections_managed_ses_company
  on broadcast_sender_connections (company_id)
  where provider = 'AMAZON_SES'
    and management_mode = 'MESSAGING_MANAGED'
    and status <> 'DISABLED';

create table managed_email_tenants (
  id bigint primary key,
  company_id bigint not null,
  broadcast_connection_id bigint,
  region varchar(64) not null,
  provider_tenant_name varchar(128) not null,
  provider_configuration_set_name varchar(128) not null,
  status varchar(32) not null,
  sending_status varchar(32) not null,
  reputation_status varchar(32),
  suspension_reason varchar(128),
  ramp_stage varchar(32) not null,
  last_provider_sync_at timestamp(6) with time zone,
  suspended_at timestamp(6) with time zone,
  created_at timestamp(6) with time zone not null,
  updated_at timestamp(6) with time zone not null,
  version bigint not null default 0,
  constraint fk_managed_email_tenants_company
    foreign key (company_id) references companies (id) on delete restrict,
  constraint fk_managed_email_tenants_broadcast_connection
    foreign key (broadcast_connection_id) references broadcast_sender_connections (id) on delete set null,
  constraint uk_managed_email_tenants_company unique (company_id),
  constraint uk_managed_email_tenants_provider_tenant unique (provider_tenant_name),
  constraint uk_managed_email_tenants_configuration_set unique (provider_configuration_set_name)
);

create unique index uk_managed_email_tenants_broadcast_connection
  on managed_email_tenants (broadcast_connection_id)
  where broadcast_connection_id is not null;

create index idx_managed_email_tenants_status_region
  on managed_email_tenants (status, region);

create table managed_email_domains (
  id bigint primary key,
  company_id bigint not null,
  tenant_id bigint not null,
  domain_type varchar(32) not null,
  normalized_domain varchar(253) not null,
  display_domain varchar(253) not null,
  hosted_slug varchar(63),
  hosted_reservation_token varchar(32),
  status varchar(40) not null,
  provider_identity_name varchar(320),
  verification_status varchar(32) not null,
  dkim_status varchar(32) not null,
  dmarc_status varchar(32) not null,
  dmarc_policy varchar(32),
  last_dns_check_at timestamp(6) with time zone,
  last_provider_sync_at timestamp(6) with time zone,
  verification_failed_at timestamp(6) with time zone,
  activated_at timestamp(6) with time zone,
  suspended_at timestamp(6) with time zone,
  released_at timestamp(6) with time zone,
  created_by_user_id bigint not null,
  created_at timestamp(6) with time zone not null,
  updated_at timestamp(6) with time zone not null,
  version bigint not null default 0,
  constraint fk_managed_email_domains_company
    foreign key (company_id) references companies (id) on delete restrict,
  constraint fk_managed_email_domains_tenant
    foreign key (tenant_id) references managed_email_tenants (id) on delete restrict,
  constraint fk_managed_email_domains_created_by
    foreign key (created_by_user_id) references users (id),
  constraint ck_managed_email_domains_hosted_fields
    check (
      (domain_type = 'UNIFYDESK_HOSTED' and hosted_slug is not null and hosted_reservation_token is not null)
      or
      (domain_type = 'CUSTOMER_DOMAIN' and hosted_slug is null and hosted_reservation_token is null)
    )
);

create unique index uk_managed_email_customer_domain_live
  on managed_email_domains (lower(normalized_domain))
  where domain_type = 'CUSTOMER_DOMAIN'
    and status not in ('DELETED', 'RETIRED');

create unique index uk_managed_email_hosted_domain_permanent
  on managed_email_domains (lower(normalized_domain))
  where domain_type = 'UNIFYDESK_HOSTED';

create index idx_managed_email_domains_company_status
  on managed_email_domains (company_id, status, updated_at desc);

create index idx_managed_email_domains_tenant_status
  on managed_email_domains (tenant_id, status);

create table managed_email_dns_records (
  id bigint primary key,
  domain_id bigint not null,
  company_id bigint not null,
  record_purpose varchar(32) not null,
  record_type varchar(16) not null,
  record_name varchar(512) not null,
  record_value varchar(2048) not null,
  priority integer,
  required boolean not null,
  status varchar(32) not null,
  source varchar(32) not null,
  last_checked_at timestamp(6) with time zone,
  created_at timestamp(6) with time zone not null,
  updated_at timestamp(6) with time zone not null,
  constraint fk_managed_email_dns_records_domain
    foreign key (domain_id) references managed_email_domains (id) on delete cascade,
  constraint fk_managed_email_dns_records_company
    foreign key (company_id) references companies (id) on delete restrict,
  constraint uk_managed_email_dns_record
    unique (domain_id, record_purpose, record_type, record_name, record_value)
);

create index idx_managed_email_dns_records_domain_required
  on managed_email_dns_records (domain_id, required, status);

create index idx_managed_email_dns_records_company_domain
  on managed_email_dns_records (company_id, domain_id);

create table managed_email_sender_bindings (
  sender_id bigint primary key,
  company_id bigint not null,
  domain_id bigint not null,
  tenant_id bigint not null,
  created_at timestamp(6) with time zone not null,
  updated_at timestamp(6) with time zone not null,
  constraint fk_managed_email_sender_bindings_sender
    foreign key (sender_id) references broadcast_senders (id) on delete restrict,
  constraint fk_managed_email_sender_bindings_company
    foreign key (company_id) references companies (id) on delete restrict,
  constraint fk_managed_email_sender_bindings_domain
    foreign key (domain_id) references managed_email_domains (id) on delete restrict,
  constraint fk_managed_email_sender_bindings_tenant
    foreign key (tenant_id) references managed_email_tenants (id) on delete restrict
);

create index idx_managed_email_sender_bindings_company_domain
  on managed_email_sender_bindings (company_id, domain_id);

create index idx_managed_email_sender_bindings_tenant
  on managed_email_sender_bindings (tenant_id);

create table managed_email_provisioning_operations (
  id bigint primary key,
  company_id bigint not null,
  resource_type varchar(32) not null,
  resource_id bigint not null,
  operation_type varchar(48) not null,
  idempotency_key varchar(255) not null,
  status varchar(32) not null,
  checkpoint varchar(64),
  attempt_count integer not null default 0,
  next_attempt_at timestamp(6) with time zone,
  claimed_at timestamp(6) with time zone,
  last_error_code varchar(128),
  last_error_message varchar(1000),
  created_at timestamp(6) with time zone not null,
  updated_at timestamp(6) with time zone not null,
  completed_at timestamp(6) with time zone,
  constraint fk_managed_email_provisioning_operations_company
    foreign key (company_id) references companies (id) on delete restrict,
  constraint uk_managed_email_provisioning_operations_key unique (idempotency_key)
);

create index idx_managed_email_provisioning_operations_claim
  on managed_email_provisioning_operations (status, next_attempt_at, id);

create index idx_managed_email_provisioning_operations_resource
  on managed_email_provisioning_operations (company_id, resource_type, resource_id);

insert into entitlement_definitions
  (id, entitlement_key, name, description, value_type, category, default_value, active, created_at, updated_at)
values
  (910000000000000023, 'MANAGED_EMAIL_ENABLED', 'Managed Email', 'Enable UnifyDesk Managed Email for the workspace', 'BOOLEAN', 'FEATURE', 'false', true, now(), now()),
  (910000000000000024, 'MANAGED_EMAIL_DOMAINS', 'Managed Email domains', 'Maximum active or provisioning Managed Email domains', 'INTEGER', 'BROADCAST', '0', true, now(), now()),
  (910000000000000025, 'MANAGED_EMAIL_SENDERS', 'Managed Email senders', 'Maximum Managed Email sender identities', 'INTEGER', 'BROADCAST', '0', true, now(), now())
on conflict (entitlement_key) do update set
  name = excluded.name,
  description = excluded.description,
  value_type = excluded.value_type,
  category = excluded.category,
  default_value = excluded.default_value,
  active = excluded.active,
  updated_at = now();
