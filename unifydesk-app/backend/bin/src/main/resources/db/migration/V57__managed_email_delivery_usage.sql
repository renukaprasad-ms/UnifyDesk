create table managed_email_usage_periods (
  company_id bigint not null,
  period_start date not null,
  accepted_count bigint not null default 0,
  reserved_count bigint not null default 0,
  adjusted_count bigint not null default 0,
  updated_at timestamp(6) with time zone not null,
  version bigint not null default 0,
  constraint pk_managed_email_usage_periods primary key (company_id, period_start),
  constraint fk_managed_email_usage_periods_company
    foreign key (company_id) references companies (id) on delete restrict
);

create table managed_email_usage_events (
  id bigint primary key,
  company_id bigint not null,
  recipient_id bigint not null,
  run_id bigint not null,
  provider_message_id varchar(512),
  event_type varchar(32) not null,
  units bigint not null,
  period_start date not null,
  idempotency_key varchar(255) not null,
  created_at timestamp(6) with time zone not null,
  metadata_json text,
  constraint uk_managed_email_usage_event_key unique (idempotency_key),
  constraint fk_managed_email_usage_events_company
    foreign key (company_id) references companies (id) on delete restrict,
  constraint fk_managed_email_usage_events_recipient
    foreign key (recipient_id) references broadcast_recipients (id) on delete restrict,
  constraint fk_managed_email_usage_events_run
    foreign key (run_id) references broadcast_runs (id) on delete restrict
);

create index idx_managed_email_usage_events_company_period
  on managed_email_usage_events (company_id, period_start, created_at);
create index idx_managed_email_usage_events_recipient
  on managed_email_usage_events (recipient_id, created_at);
create index idx_managed_email_usage_events_provider_message
  on managed_email_usage_events (provider_message_id)
  where provider_message_id is not null;

create table managed_email_provider_events (
  id bigint primary key,
  sns_message_id varchar(128) not null,
  normalized_event_key varchar(255) not null,
  company_id bigint,
  tenant_id bigint,
  domain_id bigint,
  sender_id bigint,
  run_id bigint,
  recipient_id bigint,
  provider_message_id varchar(512),
  event_type varchar(40) not null,
  event_timestamp timestamp(6) with time zone not null,
  bounce_type varchar(32),
  diagnostic_code varchar(128),
  correlation_status varchar(32) not null,
  payload_hash varchar(128) not null,
  received_at timestamp(6) with time zone not null,
  processed_at timestamp(6) with time zone,
  constraint uk_managed_email_provider_events_sns unique (sns_message_id),
  constraint uk_managed_email_provider_events_normalized unique (normalized_event_key)
);

create index idx_managed_email_provider_events_provider
  on managed_email_provider_events (provider_message_id, event_type);
create index idx_managed_email_provider_events_company_time
  on managed_email_provider_events (company_id, event_timestamp desc);
create index idx_managed_email_provider_events_recipient_time
  on managed_email_provider_events (recipient_id, event_timestamp);

create table managed_email_recipient_delivery (
  recipient_id bigint primary key,
  company_id bigint not null,
  provider_message_id varchar(512),
  sent_event_at timestamp(6) with time zone,
  delivered_at timestamp(6) with time zone,
  complained_at timestamp(6) with time zone,
  rejected_at timestamp(6) with time zone,
  first_open_at timestamp(6) with time zone,
  last_open_at timestamp(6) with time zone,
  open_count bigint not null default 0,
  first_click_at timestamp(6) with time zone,
  last_click_at timestamp(6) with time zone,
  click_count bigint not null default 0,
  last_delivery_delay_at timestamp(6) with time zone,
  last_event_at timestamp(6) with time zone not null,
  updated_at timestamp(6) with time zone not null,
  version bigint not null default 0,
  constraint fk_managed_email_recipient_delivery_recipient
    foreign key (recipient_id) references broadcast_recipients (id) on delete restrict,
  constraint fk_managed_email_recipient_delivery_company
    foreign key (company_id) references companies (id) on delete restrict
);

create index idx_managed_email_recipient_delivery_company
  on managed_email_recipient_delivery (company_id, updated_at desc);
create index idx_managed_email_recipient_delivery_provider
  on managed_email_recipient_delivery (provider_message_id)
  where provider_message_id is not null;

create table managed_email_reputation_snapshots (
  id bigint primary key,
  company_id bigint not null,
  tenant_id bigint not null,
  window_start timestamp(6) with time zone not null,
  window_end timestamp(6) with time zone not null,
  accepted_count bigint not null,
  hard_bounce_count bigint not null,
  complaint_count bigint not null,
  hard_bounce_rate numeric(12,8) not null,
  complaint_rate numeric(12,8) not null,
  provider_reputation_status varchar(64),
  decision varchar(32) not null,
  created_at timestamp(6) with time zone not null,
  constraint fk_managed_email_reputation_company
    foreign key (company_id) references companies (id) on delete restrict
);

create index idx_managed_email_reputation_company_window
  on managed_email_reputation_snapshots (company_id, window_end desc);

insert into entitlement_definitions
  (id, entitlement_key, name, description, value_type, category, default_value,
   active, created_at, updated_at)
values
  (910000000000000026, 'MANAGED_EMAIL_MESSAGES_MONTHLY',
   'Managed Email messages per month',
   'Maximum provider-accepted UnifyDesk Managed Email messages per UTC calendar month',
   'INTEGER', 'BROADCAST', '0', true, now(), now())
on conflict (entitlement_key) do nothing;
