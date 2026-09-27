create table managed_sms_run_bindings (
  run_id bigint primary key,
  company_id bigint not null,
  tenant_id bigint not null,
  dlt_profile_id bigint not null,
  pe_id_snapshot varchar(160) not null,
  header_id bigint not null,
  header_snapshot varchar(32) not null,
  template_id bigint not null,
  template_version_id bigint not null,
  dlt_template_id_snapshot varchar(255) not null,
  approved_body_snapshot text not null,
  body_fingerprint_snapshot varchar(128) not null,
  variables_schema_snapshot text not null,
  cta_policy_snapshot text not null,
  provider_binding_id bigint not null,
  provider_client_ref_snapshot varchar(255),
  provider_template_ref_snapshot varchar(255),
  provider_flow_ref_snapshot varchar(255),
  ruleset_version_snapshot varchar(80) not null,
  created_at timestamp(6) with time zone not null,
  constraint fk_managed_sms_run_bindings_run
    foreign key (run_id) references broadcast_runs(id) on delete restrict,
  constraint fk_managed_sms_run_bindings_company
    foreign key (company_id) references companies(id) on delete restrict
);

create index idx_managed_sms_run_bindings_company
  on managed_sms_run_bindings(company_id, created_at desc);

create table managed_sms_correlations (
  correlation_token varchar(52) primary key,
  company_id bigint not null,
  run_id bigint not null,
  recipient_id bigint not null,
  attempt_no integer not null,
  provider_request_id varchar(255),
  created_at timestamp(6) with time zone not null,
  expires_at timestamp(6) with time zone not null,
  constraint uk_managed_sms_correlation_attempt unique(recipient_id, attempt_no),
  constraint fk_managed_sms_correlations_company
    foreign key (company_id) references companies(id) on delete restrict,
  constraint fk_managed_sms_correlations_run
    foreign key (run_id) references broadcast_runs(id) on delete restrict,
  constraint fk_managed_sms_correlations_recipient
    foreign key (recipient_id) references broadcast_recipients(id) on delete restrict
);

create index idx_managed_sms_correlations_request
  on managed_sms_correlations(provider_request_id)
  where provider_request_id is not null;

create table managed_sms_usage_reservations (
  id bigint primary key,
  company_id bigint not null,
  run_id bigint not null,
  recipient_id bigint not null,
  attempt_no integer not null,
  estimated_segments integer not null,
  state varchar(32) not null,
  idempotency_key varchar(255) not null,
  provider_credit_snapshot numeric(18,6),
  created_at timestamp(6) with time zone not null,
  updated_at timestamp(6) with time zone not null,
  constraint uk_managed_sms_usage_reservation_key unique(idempotency_key),
  constraint uk_managed_sms_usage_reservation_attempt unique(recipient_id, attempt_no),
  constraint ck_managed_sms_usage_reservation_state
    check (state in ('RESERVED','COMMITTED','RELEASED','HELD_UNKNOWN')),
  constraint ck_managed_sms_usage_reservation_segments check (estimated_segments > 0),
  constraint fk_managed_sms_usage_company
    foreign key (company_id) references companies(id) on delete restrict,
  constraint fk_managed_sms_usage_run
    foreign key (run_id) references broadcast_runs(id) on delete restrict,
  constraint fk_managed_sms_usage_recipient
    foreign key (recipient_id) references broadcast_recipients(id) on delete restrict
);

create index idx_managed_sms_usage_company_time
  on managed_sms_usage_reservations(company_id, created_at desc);

create table managed_sms_provider_events (
  id bigint primary key,
  provider varchar(40) not null,
  provider_account_key varchar(255) not null,
  provider_event_key varchar(128) not null,
  raw_hash varchar(128) not null,
  event_type varchar(40) not null,
  correlation_token varchar(52),
  provider_request_id varchar(255),
  payload_json text not null,
  trust_status varchar(32) not null,
  processing_status varchar(32) not null,
  received_at timestamp(6) with time zone not null,
  processed_at timestamp(6) with time zone,
  constraint uk_managed_sms_provider_event
    unique(provider, provider_account_key, provider_event_key),
  constraint ck_managed_sms_event_trust
    check (trust_status in ('TRUSTED','REJECTED')),
  constraint ck_managed_sms_event_processing
    check (processing_status in ('PENDING','PROCESSING','PROCESSED','REJECTED'))
);

create index idx_managed_sms_provider_events_claim
  on managed_sms_provider_events(processing_status, received_at, id);
create index idx_managed_sms_provider_events_correlation
  on managed_sms_provider_events(correlation_token, received_at desc);

create table managed_sms_recipient_delivery (
  recipient_id bigint primary key,
  company_id bigint not null,
  accepted_at timestamp(6) with time zone,
  delivered_at timestamp(6) with time zone,
  failed_at timestamp(6) with time zone,
  rejected_at timestamp(6) with time zone,
  last_provider_status varchar(40),
  last_failure_code varchar(128),
  last_failure_reason varchar(1000),
  last_event_id bigint,
  updated_at timestamp(6) with time zone not null,
  constraint fk_managed_sms_delivery_recipient
    foreign key (recipient_id) references broadcast_recipients(id) on delete restrict,
  constraint fk_managed_sms_delivery_company
    foreign key (company_id) references companies(id) on delete restrict
);

create index idx_managed_sms_delivery_company
  on managed_sms_recipient_delivery(company_id, updated_at desc);

create table managed_sms_provider_snapshots (
  id bigint primary key,
  provider_account_key varchar(255) not null,
  provider_client_ref varchar(255),
  balance numeric(18,6),
  credit_limit numeric(18,6),
  max_send_rate numeric(18,6),
  sending_enabled boolean not null,
  observed_at timestamp(6) with time zone not null,
  valid_until timestamp(6) with time zone not null,
  constraint uk_managed_sms_provider_snapshot
    unique(provider_account_key, observed_at)
);

create index idx_managed_sms_provider_snapshot_latest
  on managed_sms_provider_snapshots(provider_account_key, observed_at desc);

insert into entitlement_definitions
  (id, entitlement_key, name, description, value_type, category, default_value,
   active, created_at, updated_at)
values
  (910000000000000027, 'MANAGED_SMS_SEGMENTS_MONTHLY',
   'Managed SMS segments per month',
   'Maximum provider-accepted UnifyDesk Managed SMS segments per UTC calendar month',
   'INTEGER', 'BROADCAST', '0', true, now(), now())
on conflict (entitlement_key) do nothing;
