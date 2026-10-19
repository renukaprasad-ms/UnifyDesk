create unique index if not exists uk_broadcast_sender_connections_managed_msg91_sms_company
  on broadcast_sender_connections (company_id)
  where provider = 'MSG91_SMS'
    and management_mode = 'MESSAGING_MANAGED'
    and status <> 'DISABLED';

create table managed_sms_tenants (
  id bigint primary key,
  company_id bigint not null,
  broadcast_connection_id bigint not null,
  status varchar(32) not null,
  provider_account_ref varchar(255),
  provider_client_ref varchar(255),
  provider_mode varchar(32) not null,
  sending_enabled boolean not null default false,
  suspended_reason varchar(255),
  last_provider_sync_at timestamp(6) with time zone,
  created_at timestamp(6) with time zone not null,
  updated_at timestamp(6) with time zone not null,
  lock_version bigint not null default 0,
  constraint fk_managed_sms_tenant_company foreign key (company_id) references companies (id) on delete restrict,
  constraint fk_managed_sms_tenant_connection foreign key (broadcast_connection_id) references broadcast_sender_connections (id) on delete restrict,
  constraint uk_managed_sms_tenant_company unique (company_id),
  constraint uk_managed_sms_tenant_connection unique (broadcast_connection_id)
);
create index idx_managed_sms_tenant_status on managed_sms_tenants (status, updated_at);

create table managed_sms_dlt_profiles (
  id bigint primary key,
  company_id bigint not null,
  tenant_id bigint not null,
  legal_name varchar(255) not null,
  dlt_platform varchar(80) not null,
  pe_id varchar(160) not null,
  status varchar(40) not null,
  tm_chain_status varchar(40) not null,
  tm_chain_reference varchar(255),
  evidence_json text,
  ruleset_version varchar(80) not null,
  verified_at timestamp(6) with time zone,
  checked_at timestamp(6) with time zone,
  stale_after timestamp(6) with time zone,
  archived_at timestamp(6) with time zone,
  created_at timestamp(6) with time zone not null,
  updated_at timestamp(6) with time zone not null,
  lock_version bigint not null default 0,
  constraint fk_managed_sms_dlt_profile_company foreign key (company_id) references companies (id) on delete restrict,
  constraint fk_managed_sms_dlt_profile_tenant foreign key (tenant_id) references managed_sms_tenants (id) on delete restrict
);
create unique index uk_managed_sms_live_pe on managed_sms_dlt_profiles (company_id, pe_id) where archived_at is null;
create index idx_managed_sms_dlt_profile_review on managed_sms_dlt_profiles (status, updated_at) where archived_at is null;

create table managed_sms_headers (
  id bigint primary key,
  company_id bigint not null,
  dlt_profile_id bigint not null,
  header varchar(32) not null,
  message_category varchar(32) not null,
  status varchar(40) not null,
  provider_mapping_status varchar(40) not null,
  approved_at timestamp(6) with time zone,
  checked_at timestamp(6) with time zone,
  stale_after timestamp(6) with time zone,
  rejection_reason varchar(255),
  archived_at timestamp(6) with time zone,
  created_at timestamp(6) with time zone not null,
  updated_at timestamp(6) with time zone not null,
  lock_version bigint not null default 0,
  constraint fk_managed_sms_header_company foreign key (company_id) references companies (id) on delete restrict,
  constraint fk_managed_sms_header_profile foreign key (dlt_profile_id) references managed_sms_dlt_profiles (id) on delete restrict
);
create unique index uk_managed_sms_live_header on managed_sms_headers (company_id, upper(header)) where archived_at is null;
create index idx_managed_sms_header_review on managed_sms_headers (status, provider_mapping_status, updated_at) where archived_at is null;

create table managed_sms_templates (
  id bigint primary key,
  company_id bigint not null,
  name varchar(160) not null,
  purpose varchar(32) not null,
  status varchar(32) not null,
  created_by_user_id bigint not null,
  archived_at timestamp(6) with time zone,
  created_at timestamp(6) with time zone not null,
  updated_at timestamp(6) with time zone not null,
  lock_version bigint not null default 0,
  constraint fk_managed_sms_template_company foreign key (company_id) references companies (id) on delete restrict,
  constraint fk_managed_sms_template_creator foreign key (created_by_user_id) references users (id) on delete restrict
);
create index idx_managed_sms_template_company on managed_sms_templates (company_id, updated_at desc) where archived_at is null;

create table managed_sms_template_versions (
  id bigint primary key,
  company_id bigint not null,
  template_id bigint not null,
  version_no bigint not null,
  dlt_template_id varchar(255) not null,
  body_text text not null,
  body_fingerprint varchar(128) not null,
  variables_schema_json text not null,
  cta_policy_json text not null,
  consent_policy_json text,
  opt_out_policy_json text,
  ruleset_version varchar(80) not null,
  dlt_status varchar(40) not null,
  status varchar(40) not null,
  approved_at timestamp(6) with time zone,
  checked_at timestamp(6) with time zone,
  stale_after timestamp(6) with time zone,
  created_at timestamp(6) with time zone not null,
  lock_version bigint not null default 0,
  constraint fk_managed_sms_template_version_company foreign key (company_id) references companies (id) on delete restrict,
  constraint fk_managed_sms_template_version_template foreign key (template_id) references managed_sms_templates (id) on delete restrict,
  constraint uk_managed_sms_template_version unique (template_id, version_no)
);
create unique index uk_managed_sms_company_dlt_template on managed_sms_template_versions (company_id, dlt_template_id);
create index idx_managed_sms_template_version_status on managed_sms_template_versions (company_id, status, created_at desc);

create table managed_sms_template_headers (
  template_version_id bigint not null,
  header_id bigint not null,
  company_id bigint not null,
  created_at timestamp(6) with time zone not null,
  primary key (template_version_id, header_id),
  constraint fk_managed_sms_template_header_version foreign key (template_version_id) references managed_sms_template_versions (id) on delete restrict,
  constraint fk_managed_sms_template_header_header foreign key (header_id) references managed_sms_headers (id) on delete restrict,
  constraint fk_managed_sms_template_header_company foreign key (company_id) references companies (id) on delete restrict
);
create index idx_managed_sms_template_headers_company_header on managed_sms_template_headers (company_id, header_id);

create table managed_sms_provider_bindings (
  id bigint primary key,
  company_id bigint not null,
  tenant_id bigint not null,
  template_version_id bigint not null,
  header_id bigint not null,
  provider varchar(40) not null,
  provider_template_ref varchar(255),
  provider_flow_ref varchar(255),
  provider_mapping_ref varchar(255),
  status varchar(40) not null,
  last_sync_at timestamp(6) with time zone,
  rejection_reason varchar(255),
  version bigint not null default 0,
  created_at timestamp(6) with time zone not null,
  updated_at timestamp(6) with time zone not null,
  constraint fk_managed_sms_provider_binding_company foreign key (company_id) references companies (id) on delete restrict,
  constraint fk_managed_sms_provider_binding_tenant foreign key (tenant_id) references managed_sms_tenants (id) on delete restrict,
  constraint fk_managed_sms_provider_binding_version foreign key (template_version_id) references managed_sms_template_versions (id) on delete restrict,
  constraint fk_managed_sms_provider_binding_header foreign key (header_id) references managed_sms_headers (id) on delete restrict,
  constraint uk_managed_sms_provider_binding unique (template_version_id, header_id, provider)
);
create index idx_managed_sms_provider_binding_status on managed_sms_provider_bindings (company_id, status, updated_at);

create table managed_sms_sender_bindings (
  sender_id bigint primary key,
  company_id bigint not null,
  tenant_id bigint not null,
  header_id bigint not null,
  created_at timestamp(6) with time zone not null,
  constraint fk_managed_sms_sender_binding_sender foreign key (sender_id) references broadcast_senders (id) on delete restrict,
  constraint fk_managed_sms_sender_binding_company foreign key (company_id) references companies (id) on delete restrict,
  constraint fk_managed_sms_sender_binding_tenant foreign key (tenant_id) references managed_sms_tenants (id) on delete restrict,
  constraint fk_managed_sms_sender_binding_header foreign key (header_id) references managed_sms_headers (id) on delete restrict,
  constraint uk_managed_sms_sender_binding_header unique (company_id, header_id)
);
create index idx_managed_sms_sender_binding_tenant on managed_sms_sender_bindings (tenant_id);

create table managed_sms_provisioning_operations (
  id bigint primary key,
  company_id bigint not null,
  tenant_id bigint not null,
  resource_type varchar(40) not null,
  resource_id bigint not null,
  operation_type varchar(64) not null,
  idempotency_key varchar(255) not null,
  status varchar(32) not null,
  attempt_count integer not null default 0,
  next_attempt_at timestamp(6) with time zone,
  lease_until timestamp(6) with time zone,
  last_error_code varchar(128),
  last_error_message varchar(1000),
  created_at timestamp(6) with time zone not null,
  updated_at timestamp(6) with time zone not null,
  constraint fk_managed_sms_provisioning_company foreign key (company_id) references companies (id) on delete restrict,
  constraint fk_managed_sms_provisioning_tenant foreign key (tenant_id) references managed_sms_tenants (id) on delete restrict,
  constraint uk_managed_sms_provisioning_key unique (idempotency_key)
);
create index idx_managed_sms_provisioning_claim on managed_sms_provisioning_operations (status, next_attempt_at, lease_until, id);

create table managed_sms_admin_actions (
  id bigint primary key,
  company_id bigint not null,
  actor_user_id bigint not null,
  resource_type varchar(40) not null,
  resource_id bigint not null,
  action varchar(40) not null,
  reason varchar(500) not null,
  before_state_json text not null,
  after_state_json text not null,
  created_at timestamp(6) with time zone not null,
  constraint fk_managed_sms_admin_action_company foreign key (company_id) references companies (id) on delete restrict,
  constraint fk_managed_sms_admin_action_actor foreign key (actor_user_id) references users (id) on delete restrict
);
create index idx_managed_sms_admin_action_company_created on managed_sms_admin_actions (company_id, created_at desc);

create or replace function managed_sms_protect_activated_template()
returns trigger language plpgsql as $$
begin
  if old.status in ('ACTIVE','SUPERSEDED','SUSPENDED','STALE','ARCHIVED')
     and (
       new.dlt_template_id is distinct from old.dlt_template_id
       or new.body_text is distinct from old.body_text
       or new.body_fingerprint is distinct from old.body_fingerprint
       or new.variables_schema_json is distinct from old.variables_schema_json
       or new.cta_policy_json is distinct from old.cta_policy_json
       or new.consent_policy_json is distinct from old.consent_policy_json
       or new.opt_out_policy_json is distinct from old.opt_out_policy_json
       or new.ruleset_version is distinct from old.ruleset_version
     ) then
    raise exception 'Activated managed SMS template content is immutable';
  end if;
  return new;
end;
$$;
create trigger trg_managed_sms_template_immutable
before update on managed_sms_template_versions
for each row execute function managed_sms_protect_activated_template();
