create table campaign_launch_runs (
  id bigint primary key,
  company_id bigint not null,
  reachlyo_campaign_id bigint not null,
  idempotency_key varchar(160) not null,
  status varchar(32) not null,
  requested_by_user_id bigint not null,
  plan_snapshot_json text,
  validation_issues_json text,
  last_error_code varchar(160),
  last_error_message varchar(2000),
  started_at timestamp(6) with time zone,
  completed_at timestamp(6) with time zone,
  version bigint not null default 0,
  created_at timestamp(6) with time zone not null,
  updated_at timestamp(6) with time zone not null,
  constraint fk_campaign_launch_runs_company foreign key (company_id) references companies (id) on delete cascade,
  constraint fk_campaign_launch_runs_campaign foreign key (reachlyo_campaign_id) references reachlyo_campaigns (id) on delete cascade,
  constraint uk_campaign_launch_run_idempotency unique (company_id, reachlyo_campaign_id, idempotency_key)
);
create index idx_campaign_launch_runs_company_campaign
  on campaign_launch_runs (company_id, reachlyo_campaign_id, created_at);
create index idx_campaign_launch_runs_status
  on campaign_launch_runs (company_id, status, updated_at);

create table campaign_launch_steps (
  id bigint primary key,
  company_id bigint not null,
  reachlyo_campaign_id bigint not null,
  launch_run_id bigint not null,
  campaign_channel_id bigint not null,
  provider_deployment_id bigint not null,
  ad_account_id bigint not null,
  provider varchar(24) not null,
  sequence_no integer not null,
  operation_key varchar(160) not null,
  action varchar(24) not null,
  resource varchar(40) not null,
  create_paused boolean not null default false,
  status varchar(24) not null,
  attempt_count integer not null default 0,
  step_idempotency_key varchar(220) not null,
  local_resource_id bigint,
  external_resource_id varchar(255),
  request_json text,
  response_json text,
  last_error_code varchar(160),
  last_error_message varchar(2000),
  started_at timestamp(6) with time zone,
  completed_at timestamp(6) with time zone,
  version bigint not null default 0,
  created_at timestamp(6) with time zone not null,
  updated_at timestamp(6) with time zone not null,
  constraint fk_campaign_launch_steps_company foreign key (company_id) references companies (id) on delete cascade,
  constraint fk_campaign_launch_steps_campaign foreign key (reachlyo_campaign_id) references reachlyo_campaigns (id) on delete cascade,
  constraint fk_campaign_launch_steps_run foreign key (launch_run_id) references campaign_launch_runs (id) on delete cascade,
  constraint fk_campaign_launch_steps_channel foreign key (campaign_channel_id) references campaign_channels (id) on delete cascade,
  constraint fk_campaign_launch_steps_deployment foreign key (provider_deployment_id) references provider_deployments (id) on delete cascade,
  constraint fk_campaign_launch_steps_account foreign key (ad_account_id) references ad_accounts (id),
  constraint uk_campaign_launch_step_operation unique (launch_run_id, campaign_channel_id, operation_key)
);
create index idx_campaign_launch_steps_run_sequence
  on campaign_launch_steps (launch_run_id, sequence_no);
create index idx_campaign_launch_steps_status
  on campaign_launch_steps (company_id, status, updated_at);
